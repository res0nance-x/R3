package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.NanoHTTPD.MIME_PLAINTEXT
import org.nanohttpd.protocols.http.request.Method
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status
import r3.content.Content
import r3.io.BoundedInputStream
import r3.io.log
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Represents the parsed value of an HTTP Range header. */
sealed class RangeSpec {
    /** No Range header was present. */
    object None : RangeSpec()

    /** bytes=start- (open-ended: serve from start to end of file) */
    data class From(val start: Long) : RangeSpec()

    /** bytes=start-end */
    data class Bounded(val start: Long, val end: Long) : RangeSpec()

    /** bytes=-suffixLength */
    data class Suffix(val length: Long) : RangeSpec()
}

fun parseRange(header: Map<String, String>): RangeSpec {
    val range = header["range"] ?: return RangeSpec.None
    if (!range.startsWith("bytes=")) return RangeSpec.None
    val spec = range.substring("bytes=".length)
    // Support multiple ranges by using only the first range spec (e.g. "0-99,200-299").
    val first = spec.split(',')[0].trim()
    val minus = first.indexOf('-')
    if (minus < 0) return RangeSpec.None
    return try {
        val left = first.take(minus)
        val right = first.substring(minus + 1)
        when {
            left.isEmpty() && right.isNotEmpty() -> RangeSpec.Suffix(right.toLong())
            left.isNotEmpty() && right.isEmpty() -> RangeSpec.From(left.toLong())
            left.isNotEmpty() && right.isNotEmpty() -> RangeSpec.Bounded(left.toLong(), right.toLong())
            else -> RangeSpec.None
        }
    } catch (_: NumberFormatException) {
        log("RangeRequest: failure to parse range header $spec")
        RangeSpec.None
    }
}

/**
 * Skips exactly [toSkip] bytes from [stream].
 * Throws [EOFException] if the stream ends before [toSkip] bytes have been consumed.
 * Lets any [java.io.IOException] from [InputStream.skip] or [InputStream.read] propagate normally.
 */
private fun skipFully(stream: InputStream, toSkip: Long) {
    var remaining = toSkip
    val buf = ByteArray(8192)
    while (remaining > 0) {
        val skipped = stream.skip(remaining)   // IOException propagates to caller
        if (skipped > 0) {
            remaining -= skipped
            continue
        }
        // skip() returned 0 — fall back to read-and-discard to guarantee progress
        val r = stream.read(buf, 0, minOf(remaining, buf.size.toLong()).toInt())
        when {
            r == -1 -> throw EOFException(
                "Unexpected EOF while skipping to range start " +
                "(${toSkip - remaining} of $toSkip bytes skipped)"
            )
            r > 0 -> remaining -= r
            // r == 0 → no data yet; retry on next iteration
        }
    }
}

fun rangeRequestResponse(session: IHTTPSession, content: Content): Response {
    val startNs = System.nanoTime()
    val requestHeader: Map<String, String> = session.headers
    // Unified ETag: use lastModified (content.created) and length to form a stable ETag
    val eTag = "${content.lastModified}-${content.length}"
    val rangeSpec = parseRange(requestHeader)
    val rangeHeader = rangeSpec != RangeSpec.None
    val ifRange = requestHeader["if-range"]
    val isGetOrHead = (session.method == Method.GET || session.method == Method.HEAD)

    // helper to strip surrounding quotes and weak prefix
    fun stripETag(s: String): String = s.trim().let { v ->
        var x = v
        if (x.startsWith("W/")) x = x.substring(2)
        x.trim().trim('"')
    }

    // Determine If-Range match: it can be an ETag (possibly quoted or weak) or an HTTP-date
    val headerIfRangeMissingOrMatching: Boolean = if (ifRange == null) {
        true
    } else {
        val fr = ifRange.trim()
        if (fr.startsWith("\"") || fr.startsWith("W/")) {
            stripETag(fr) == eTag
        } else {
            // try parse as RFC1123 date and compare to content.created
            try {
                val dt = ZonedDateTime.parse(fr, DateTimeFormatter.RFC_1123_DATE_TIME)
                val tms = dt.toInstant().toEpochMilli()
                // Honour Range if the resource has not been modified since the client's copy date
                tms >= content.lastModified
            } catch (_: DateTimeParseException) {
                false
            }
        }
    }

    val ifNoneMatch = requestHeader["if-none-match"]
    // support multiple ETags in If-None-Match (comma separated) and quoted/weak forms
    val headerIfNoneMatchPresentAndMatching: Boolean = ifNoneMatch != null && (
        ifNoneMatch.trim() == "*" ||
            ifNoneMatch.split(",").map { stripETag(it) }.contains(eTag)
    )
    val mimeType = MimeMap[content.ext] ?: "application/octet-stream"

    // helper to format Last-Modified header from content.created
    val lastModifiedHeader: String = try {
        DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(content.lastModified))
    } catch (_: Exception) {
        ""
    }

    // RFC 7232 §4.1: 304 MUST NOT include a message body.
    fun notModifiedResponse(): Response =
        newFixedLengthResponse(Status.NOT_MODIFIED, mimeType, ByteArrayInputStream(ByteArray(0)), 0L).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("ETag", '"' + eTag + '"')
            if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
            addHeader("X-Server-Time-ms", ((System.nanoTime() - startNs) / 1_000_000L).toString())
        }

    // Conditional GET/HEAD: If-None-Match has priority over If-Modified-Since
    if (isGetOrHead && headerIfNoneMatchPresentAndMatching) {
        return notModifiedResponse()
    }

    // Support If-Modified-Since as a conditional GET when If-None-Match is not present
    val ifModifiedSince = requestHeader["if-modified-since"]
    if (isGetOrHead && ifNoneMatch == null && ifModifiedSince != null) {
        try {
            val dt = ZonedDateTime.parse(ifModifiedSince, DateTimeFormatter.RFC_1123_DATE_TIME)
            val tms = dt.toInstant().toEpochMilli()
            if (content.lastModified <= tms) {
                return notModifiedResponse()
            }
        } catch (_: Exception) {
            // ignore parse errors and continue
        }
    }

    // Resolve the range spec to concrete start/end byte offsets.
    // reqEnd == -1L means "to end of file" (resolved later once content.length is known).
    var reqStart: Long
    var reqEnd: Long
    when (rangeSpec) {
        is RangeSpec.None -> { reqStart = 0L; reqEnd = -1L }
        is RangeSpec.From -> { reqStart = rangeSpec.start; reqEnd = -1L }
        is RangeSpec.Bounded -> { reqStart = rangeSpec.start; reqEnd = rangeSpec.end }
        is RangeSpec.Suffix -> {
            val suffix = rangeSpec.length
            reqStart = when {
                suffix <= 0 || suffix >= content.length -> 0L
                else -> content.length - suffix
            }
            reqEnd = -1L
        }
    }

    // If end is specified and it's before start, that's an invalid range -> 416
    if (reqEnd in 0..<reqStart) {
        return newFixedLengthResponse(
            Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT,
            ""
        ).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Content-Range", "bytes */${content.length}")
            addHeader("ETag", '"' + eTag + '"')
        }
    }

    // Zero-length resources cannot satisfy any byte-range request
    if (rangeHeader && headerIfRangeMissingOrMatching && content.length == 0L) {
        return newFixedLengthResponse(
            Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT,
            ""
        ).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Content-Range", "bytes */0")
            addHeader("ETag", '"' + eTag + '"')
            if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
            addHeader("X-Server-Time-ms", ((System.nanoTime() - startNs) / 1_000_000L).toString())
        }
    }

    val response =
        if (headerIfRangeMissingOrMatching && rangeHeader && reqStart >= 0 && reqStart < content.length) {
            if (reqEnd < 0 || reqEnd >= content.length) {
                reqEnd = content.length - 1
            }
            // coerceAtLeast(0) guards against the pathological Long.MAX_VALUE + 1 overflow case
            val newLen = (reqEnd - reqStart + 1).coerceAtLeast(0L)
            if (session.method == Method.GET) {
                // Open the stream and protect against a leak if skipFully throws
                val stream = content.createInputStream()
                try {
                    skipFully(stream, reqStart)
                } catch (e: Exception) {
                    stream.close()
                    throw e
                }
                newFixedLengthResponse(
                    Status.PARTIAL_CONTENT, mimeType,
                    BufferedInputStream(BoundedInputStream(stream, newLen)), newLen
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Range", "bytes ${reqStart}-${reqEnd}/${content.length}")
                    addHeader("ETag", '"' + eTag + '"')
                    if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
                    addHeader("X-Server-Time-ms", ((System.nanoTime() - startNs) / 1_000_000L).toString())
                }
            } else {
                newFixedLengthResponse(
                    Status.PARTIAL_CONTENT, mimeType,
                    ByteArrayInputStream(ByteArray(0)), newLen
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Range", "bytes ${reqStart}-${reqEnd}/${content.length}")
                    addHeader("ETag", '"' + eTag + '"')
                    if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
                    addHeader("X-Server-Time-ms", ((System.nanoTime() - startNs) / 1_000_000L).toString())
                }
            }
        } else {
            if (headerIfRangeMissingOrMatching && rangeHeader && reqStart >= content.length) {
                newFixedLengthResponse(
                    Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT,
                    ""
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Range", "bytes */${content.length}")
                    addHeader("ETag", '"' + eTag + '"')
                    if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
                    addHeader("X-Server-Time-ms", ((System.nanoTime() - startNs) / 1_000_000L).toString())
                }
            } else {
                val istream = if (session.method != Method.HEAD) {
                    BufferedInputStream(content.createInputStream())
                } else {
                    ByteArrayInputStream(ByteArray(0))
                }
                newFixedLengthResponse(
                    Status.OK, mimeType,
                    istream, content.length
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("ETag", '"' + eTag + '"')
                    if (lastModifiedHeader.isNotEmpty()) addHeader("Last-Modified", lastModifiedHeader)
                    addHeader("X-Server-Time-ms", ((System.nanoTime() - startNs) / 1_000_000L).toString())
                }
            }
        }
    val filename = content.path.substringAfterLast('/').substringAfterLast('\\')
    if (filename.isNotEmpty() && (response.status == Status.OK || response.status == Status.PARTIAL_CONTENT)) {
        val encodedFilename = try {
            java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
        } catch (e: Exception) {
            filename
        }
        response.addHeader("Content-Disposition", "inline; filename=\"$encodedFilename\"; filename*=UTF-8''$encodedFilename")
    }
    return response
}
