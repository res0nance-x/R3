package r3.pack

import r3.content.Content
import r3.http.MimeMap
import r3.io.BoundedInputStream
import r3.io.CountingInputStream
import r3.io.DEFAULT_COPY_BUFFER_SIZE
import r3.io.QuotedPrintableInputStream
import r3.io.skipFullBytes
import r3.source.FileSource
import r3.source.Source
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.*

/**
 * An in-place [Pack] adapter for MHTML (MIME encapsulation of aggregate HTML documents, RFC 2557).
 *
 * Scans the MHTML document once upon initialization to index part boundaries, headers,
 * and byte ranges. Individual resources (images, fonts, stylesheets, scripts) are streamed
 * and decoded on demand without extracting them to disk or loading the entire archive into RAM.
 *
 * @param src The underlying data source containing the MHTML file.
 * @param rewriteLinks If true (default), the root HTML document is rewritten so that absolute
 *                     web URLs, query-string media URLs, and Content-ID references point to local
 *                     relative paths within the pack.
 */
class MhtmlPack(val src: Source, val rewriteLinks: Boolean = true) : Pack {
	constructor(file: File, rewriteLinks: Boolean = true) : this(FileSource(file), rewriteLinks)

	private val contentList = ArrayList<Content>()
	private val contentMap = HashMap<String, Content>()
	private val locationMap = HashMap<String, Content>()

	var title: String? = null
		private set
	var snapshotLocation: String? = null
		private set
	var date: String? = null
		private set

	init {
		val parsedParts = scan(src)
		if (parsedParts.isNotEmpty()) {
			buildContents(parsedParts)
		}
	}

	override fun iterator(): Iterator<Content> {
		return contentList.iterator()
	}

	operator fun get(path: String): Content? {
		contentMap[path]?.let { return it }
		val encoded = PackMap.encodePath(path)
		if (encoded != path) {
			contentMap[encoded]?.let { return it }
		}
		val decoded = runCatching { URLDecoder.decode(path, Charsets.UTF_8.name()) }.getOrNull()
		if (decoded != null && decoded != path) {
			contentMap[decoded]?.let { return it }
		}
		return locationMap[path]
	}

	fun getByLocation(location: String): Content? = locationMap[location]

	val size: Int
		get() = contentList.size

	private class RawPart(
		val headers: Map<String, String>,
		val contentType: String,
		val mediaType: String,
		val charset: String,
		val contentLocation: String?,
		val contentId: String?,
		val transferEncoding: String,
		val payloadStartOffset: Long,
		val payloadRawLength: Long,
		val decodedLength: Long
	)

	private fun scan(src: Source): List<RawPart> {
		val istream = src.createInputStream().buffered(DEFAULT_COPY_BUFFER_SIZE)
		val cis = CountingInputStream(istream)
		val parts = ArrayList<RawPart>()

		try {
			// 1. Parse top-level headers
			val topHeaders = readHeaders(cis)
			title = topHeaders["subject"]
			snapshotLocation = topHeaders["snapshot-content-location"]
			date = topHeaders["date"]

			val contentTypeHeader = topHeaders["content-type"]
				?: throw IOException("No Content-Type header found in MHTML")
			val boundary = extractBoundary(contentTypeHeader)
				?: throw IOException("No multipart boundary found in MHTML Content-Type: $contentTypeHeader")

			val boundaryBytes = ("--" + boundary).toByteArray(Charsets.US_ASCII)
			val target = ByteArray(1 + boundaryBytes.size)
			target[0] = '\n'.code.toByte()
			System.arraycopy(boundaryBytes, 0, target, 1, boundaryBytes.size)

			// 2. Advance past preamble to the first boundary line
			var line = readAsciiLine(cis)
			while (line != null) {
				if (line.startsWith("--$boundary")) {
					break
				}
				line = readAsciiLine(cis)
			}

			if (line == null || line.startsWith("--$boundary--")) {
				return parts // Empty multipart
			}

			// Precompute KMP failure table for "\n--" + boundary
			val pi = IntArray(target.size)
			var k = 0
			for (i in 1 until target.size) {
				while (k > 0 && target[k] != target[i]) {
					k = pi[k - 1]
				}
				if (target[k] == target[i]) {
					k++
				}
				pi[i] = k
			}

			// 3. Loop through parts
			var finished = false
			while (!finished) {
				val partHeaders = readHeaders(cis)
				val payloadStart = cis.count

				val rawContentType = partHeaders["content-type"] ?: "application/octet-stream"
				val mediaType = rawContentType.substringBefore(';').trim().lowercase(Locale.ROOT)
				val charset = extractParam(rawContentType, "charset") ?: "utf-8"
				val contentLocation = partHeaders["content-location"]?.trim()
				val contentId = partHeaders["content-id"]?.trim()?.removeSurrounding("<", ">")
				val transferEncoding = partHeaders["content-transfer-encoding"]?.trim()?.lowercase(Locale.ROOT) ?: "binary"

				// Scan payload until next boundary
				var j = 0
				var matchStart = -1L
				var wasCrBeforeNewline = false
				var pendingCr = false

				// Metrics for decoded length
				var b64CharCount = 0L
				var padCount = 0L
				var qpDecodedLen = 0L
				var qpState = 0 // 0: Normal, 1: Saw '=', 2: Saw '=\r', 3: Saw '=X'

				fun accountByte(b: Byte) {
					when (transferEncoding) {
						"base64" -> {
							val c = b.toInt().toChar()
							if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/') {
								b64CharCount++
							} else if (c == '=') {
								padCount++
							}
						}
						"quoted-printable" -> {
							val ub = b.toInt() and 0xFF
							when (qpState) {
								0 -> {
									if (ub == '='.code) {
										qpState = 1
									} else {
										qpDecodedLen++
									}
								}
								1 -> {
									if (ub == '\r'.code) {
										qpState = 2
									} else if (ub == '\n'.code) {
										qpState = 0 // soft break: 0 bytes
									} else if (isHex(ub)) {
										qpState = 3
									} else {
										qpDecodedLen += 2
										qpState = 0
									}
								}
								2 -> {
									if (ub == '\n'.code) {
										qpState = 0 // soft break "=\r\n": 0 bytes
									} else {
										qpDecodedLen += 2
										qpState = 0
									}
								}
								3 -> {
									if (isHex(ub)) {
										qpDecodedLen++ // "=XX" -> 1 byte
									} else {
										qpDecodedLen += 3
									}
									qpState = 0
								}
							}
						}
					}
				}

				while (true) {
					val b = cis.read()
					if (b == -1) {
						finished = true
						matchStart = cis.count
						break
					}

					if (pendingCr) {
						pendingCr = false
						if (b == '\n'.code) {
							wasCrBeforeNewline = true
						} else {
							accountByte('\r'.code.toByte())
							wasCrBeforeNewline = false
						}
					} else {
						wasCrBeforeNewline = false
					}

					if (b == '\r'.code) {
						pendingCr = true
					}

					val ub = b and 0xFF
					while (j > 0 && ub != (target[j].toInt() and 0xFF)) {
						val numToEmit = j - pi[j - 1]
						for (m in 0 until numToEmit) {
							accountByte(target[m])
						}
						j = pi[j - 1]
					}

					if (ub == (target[j].toInt() and 0xFF)) {
						j++
						if (j == target.size) {
							matchStart = cis.count - target.size
							break
						}
					} else {
						if (b != '\r'.code) {
							accountByte(b.toByte())
						}
					}
				}

				val payloadEnd = if (wasCrBeforeNewline) matchStart - 1 else matchStart
				val payloadRawLength = (payloadEnd - payloadStart).coerceAtLeast(0L)

				val decodedLength = when (transferEncoding) {
					"base64" -> {
						val totalValid = b64CharCount + padCount
						if (totalValid > 0) ((totalValid / 4) * 3 - padCount).coerceAtLeast(0L) else 0L
					}
					"quoted-printable" -> qpDecodedLen
					else -> payloadRawLength
				}

				parts.add(
					RawPart(
						headers = partHeaders,
						contentType = rawContentType,
						mediaType = mediaType,
						charset = charset,
						contentLocation = contentLocation,
						contentId = contentId,
						transferEncoding = transferEncoding,
						payloadStartOffset = payloadStart,
						payloadRawLength = payloadRawLength,
						decodedLength = decodedLength
					)
				)

				if (finished) break

				// Check if this boundary line ends with '--' (final boundary)
				val next1 = cis.read()
				val next2 = if (next1 == '-'.code) cis.read() else -1
				val isFinal = (next1 == '-'.code && next2 == '-'.code)

				// Consume rest of line until \n
				var rem = if (isFinal) cis.read() else if (next2 != -1) next2 else next1
				while (rem != -1 && rem != '\n'.code) {
					rem = cis.read()
				}

				if (isFinal || rem == -1) {
					finished = true
				}
			}
		} finally {
			cis.close()
		}

		return parts
	}

	private fun buildContents(rawParts: List<RawPart>) {
		// 1. Identify primary HTML part
		val primaryIndex = rawParts.indexOfFirst {
			snapshotLocation != null && it.contentLocation != null && it.contentLocation == snapshotLocation
		}.let { idx ->
			if (idx >= 0) idx else rawParts.indexOfFirst { it.mediaType == "text/html" }.let { if (it >= 0) it else 0 }
		}

		val assignedPaths = HashSet<String>()
		val mappedParts = ArrayList<Pair<RawPart, String>>()

		// 2. Assign clean pack paths
		for (i in rawParts.indices) {
			val part = rawParts[i]
			val path = if (i == primaryIndex) {
				"index.html"
			} else {
				determinePath(part, i, assignedPaths)
			}
			assignedPaths.add(path)
			mappedParts.add(Pair(part, path))
		}

		// 3. Build comprehensive URL lookup maps for attribute rewriting
		val lookupMap = HashMap<String, String>()
		val filenameMap = HashMap<String, String>()

		for ((part, path) in mappedParts) {
			fun register(key: String?) {
				if (key.isNullOrBlank()) return
				val trimmed = key.trim()
				lookupMap[trimmed] = path

				// Register unescaped / query-stripped variants
				val withoutQuery = trimmed.substringBefore('?').substringBefore('#')
				if (withoutQuery.isNotEmpty() && !lookupMap.containsKey(withoutQuery)) {
					lookupMap[withoutQuery] = path
				}

				// Register decoded variant
				val decoded = runCatching { URLDecoder.decode(trimmed, Charsets.UTF_8) }.getOrNull()
				if (decoded != null && decoded != trimmed) {
					lookupMap[decoded] = path
					val decWithoutQuery = decoded.substringBefore('?').substringBefore('#')
					if (decWithoutQuery.isNotEmpty() && !lookupMap.containsKey(decWithoutQuery)) {
						lookupMap[decWithoutQuery] = path
					}
				}

				// If key has filename, record in filename map (if unique)
				val fname = withoutQuery.substringAfterLast('/')
				if (fname.isNotEmpty() && fname.contains('.')) {
					filenameMap.putIfAbsent(fname, path)
				}
			}

			// Register original Location
			val loc = part.contentLocation
			if (loc != null) {
				register(loc)
				// If loc contains a nested proxy URL, also register the underlying target URL
				if (loc.contains("://") && loc.indexOf("://") != loc.lastIndexOf("://")) {
					val lastIdx = loc.lastIndexOf("://")
					val subUrl = loc.substring(lastIdx - 4) // e.g. "https://..."
					register(subUrl)
				}

				// Protocol-relative & root-relative paths
				if (loc.startsWith("http://") || loc.startsWith("https://")) {
					try {
						val uri = URI(loc)
						val pathOnly = uri.rawPath
						if (!pathOnly.isNullOrEmpty() && pathOnly.length > 1) {
							register(pathOnly)
							register(pathOnly.removePrefix("/"))
						}
						val hostAndPath = "${uri.host}${uri.rawPath}"
						register(hostAndPath)
						register("//$hostAndPath")
					} catch (_: Exception) {}
				} else if (loc.startsWith("chrome-extension://")) {
					val afterScheme = loc.substringAfter("chrome-extension://")
					val pathPart = afterScheme.substringAfter('/')
					register("/$pathPart")
					register(pathPart)
				}
			}

			// Register CID
			val cid = part.contentId
			if (cid != null) {
				val cleanCid = cid.removeSurrounding("<", ">")
				register("cid:$cleanCid")
				register(cleanCid)
				register("cid:${cleanCid.substringBefore('@')}")
				register(cleanCid.substringBefore('@'))
			}

			// Also register filename of the assigned local path itself
			val localFilename = path.substringAfterLast('/')
			filenameMap.putIfAbsent(localFilename, path)
		}

		fun resolveUrl(rawUrl: String): String? {
			if (rawUrl.isBlank() || rawUrl.startsWith("data:") || rawUrl.startsWith("blob:") || rawUrl.startsWith("javascript:")) {
				return null
			}
			val unescaped = rawUrl.trim().replace("&amp;", "&")

			// 1. Direct exact lookup
			lookupMap[unescaped]?.let { return it }

			// 2. Lookup stripped of query string and fragment
			val base = unescaped.substringBefore('?').substringBefore('#')
			lookupMap[base]?.let { return it }

			// 3. Decoded percent-encoding
			val decoded = runCatching { URLDecoder.decode(unescaped, Charsets.UTF_8) }.getOrNull()
			if (decoded != null && decoded != unescaped) {
				lookupMap[decoded]?.let { return it }
				lookupMap[decoded.substringBefore('?').substringBefore('#')]?.let { return it }
			}

			// 4. Try matching end of URL path (for absolute URLs whose domain or proxy differs)
			val cleanBase = base.substringBeforeLast('/')
			val filename = base.substringAfterLast('/')
			if (filename.isNotEmpty() && filename.contains('.')) {
				filenameMap[filename]?.let { return it }
				// Try with decoded filename
				val decFilename = runCatching { URLDecoder.decode(filename, Charsets.UTF_8) }.getOrNull()
				if (decFilename != null) {
					filenameMap[decFilename]?.let { return it }
				}
			}

			// 5. Try stripped extension matching (e.g. /gsi/style matching gsi/style.css)
			if (filename.isNotEmpty() && !filename.contains('.')) {
				lookupMap["$base.css"]?.let { return it }
				filenameMap["$filename.css"]?.let { return it }
			}

			return null
		}

		// 4. Construct Content instances
		for (i in mappedParts.indices) {
			val (part, path) = mappedParts[i]
			val isHtml = (part.mediaType == "text/html")
			val ext = if (part.mediaType == "text/html") {
				"html"
			} else if (part.mediaType == "text/css") {
				"css"
			} else {
				val extFromMime = MimeMap.getExtensionForMimeType(part.mediaType)
				if (extFromMime.isNotEmpty()) extFromMime else path.substringAfterLast('.', "")
			}.ifEmpty { "bin" }

			val content: Content = if (isHtml && rewriteLinks) {
				val rawBytes = decodePartBytes(part)
				val cs = runCatching { Charset.forName(part.charset) }.getOrDefault(Charsets.UTF_8)
				var html = String(rawBytes, cs)

				// Strip <base href="..."> tags so local relative paths resolve correctly
				html = html.replace(Regex("""<base\s+[^>]*href=["'][^"']*["'][^>]*>""", RegexOption.IGNORE_CASE), "")

				// Attribute-aware rewrite: href, src, poster, data-src, data-original, data-old-hires, data-zoom-image
				val attrRegex = Regex("""(?i)\b(href|src|poster|data-src|data-original|data-lazy-src|data-old-hires|data-zoom-image|data-hires|data-url)\s*=\s*(["'])(.*?)\2""")
				html = attrRegex.replace(html) { match ->
					val attrName = match.groupValues[1]
					val quote = match.groupValues[2]
					val originalVal = match.groupValues[3]
					val resolved = resolveUrl(originalVal)
					if (resolved != null) {
						"$attrName=$quote$resolved$quote"
					} else {
						match.value
					}
				}

				// Rewrite srcset and imagesrcset attributes
				val srcSetRegex = Regex("""(?i)\b(srcset|imagesrcset)\s*=\s*(["'])(.*?)\2""")
				html = srcSetRegex.replace(html) { match ->
					val attrName = match.groupValues[1]
					val quote = match.groupValues[2]
					val originalVal = match.groupValues[3]

					val candidates = originalVal.split(',')
					var anyResolved = false
					val newCandidates = candidates.map { candidate ->
						val trimmed = candidate.trim()
						val spaceIdx = trimmed.indexOfFirst { it.isWhitespace() }
						if (spaceIdx > 0) {
							val url = trimmed.substring(0, spaceIdx)
							val descriptor = trimmed.substring(spaceIdx)
							val resolved = resolveUrl(url)
							if (resolved != null) {
								anyResolved = true
								"$resolved$descriptor"
							} else {
								trimmed
							}
						} else {
							val resolved = resolveUrl(trimmed)
							if (resolved != null) {
								anyResolved = true
								resolved
							} else {
								trimmed
							}
						}
					}

					if (anyResolved) {
						val firstResolved = newCandidates.firstOrNull { !it.startsWith("http://") && !it.startsWith("https://") }
							?.substringBefore(' ')
						val sanitizedCandidates = newCandidates.map { cand ->
							if ((cand.startsWith("http://") || cand.startsWith("https://")) && firstResolved != null) {
								val descriptor = cand.substringAfter(' ', "")
								if (descriptor.isNotEmpty()) "$firstResolved $descriptor" else firstResolved
							} else {
								cand
							}
						}
						"$attrName=$quote${sanitizedCandidates.joinToString(", ")}$quote"
					} else {
						match.value
					}
				}

				// Rewrite JSON image attributes (e.g. Amazon's data-a-dynamic-image)
				val dynamicImgRegex = Regex("""(?i)\bdata-a-dynamic-image\s*=\s*(["'])(.*?)\1""")
				html = dynamicImgRegex.replace(html) { match ->
					val quote = match.groupValues[1]
					var jsonStr = match.groupValues[2]
					val urlInJsonRegex = Regex("""https?://[^"'\s\\]+""")
					jsonStr = urlInJsonRegex.replace(jsonStr) { urlMatch ->
						resolveUrl(urlMatch.value) ?: urlMatch.value
					}
					"data-a-dynamic-image=$quote$jsonStr$quote"
				}

				// Rewrite CSS url(...) references
				val cssUrlRegex = Regex("""(?i)\burl\(\s*(["']?)([^"')]+)\1\s*\)""")
				html = cssUrlRegex.replace(html) { match ->
					val quote = match.groupValues[1]
					val originalVal = match.groupValues[2]
					val resolved = resolveUrl(originalVal)
					if (resolved != null) {
						"url($quote$resolved$quote)"
					} else {
						match.value
					}
				}

				val rewrittenBytes = html.toByteArray(Charsets.UTF_8)
				MhtmlMemoryContent(
					path = path,
					ext = ext,
					bytes = rewrittenBytes,
					mimeType = part.contentType
				)
			} else {
				MhtmlStreamContent(
					src = src,
					path = path,
					ext = ext,
					payloadStartOffset = part.payloadStartOffset,
					payloadRawLength = part.payloadRawLength,
					length = part.decodedLength,
					transferEncoding = part.transferEncoding,
					mimeType = part.contentType
				)
			}

			contentList.add(content)
			contentMap[path] = content
			val encPath = PackMap.encodePath(path)
			if (encPath != path) contentMap.putIfAbsent(encPath, content)
			val decPath = runCatching { URLDecoder.decode(path, Charsets.UTF_8.name()) }.getOrNull()
			if (decPath != null && decPath != path) contentMap.putIfAbsent(decPath, content)
			if (part.contentLocation != null) {
				locationMap[part.contentLocation] = content
			}
			if (part.contentId != null) {
				locationMap["cid:${part.contentId}"] = content
			}
		}
	}

	private fun decodePartBytes(part: RawPart): ByteArray {
		val rawStream = src.createInputStream()
		rawStream.skipFullBytes(part.payloadStartOffset)
		val bounded = BoundedInputStream(rawStream, part.payloadRawLength)
		val decoder = when (part.transferEncoding) {
			"base64" -> Base64.getMimeDecoder().wrap(bounded)
			"quoted-printable" -> QuotedPrintableInputStream(bounded)
			else -> bounded
		}
		return decoder.use { it.readAllBytes() }
	}

	private fun determinePath(part: RawPart, index: Int, assigned: Set<String>): String {
		val extFromMime = MimeMap.getExtensionForMimeType(part.mediaType).ifEmpty { "bin" }

		val rawLoc = part.contentLocation?.trim()
		val rawCid = part.contentId?.trim()?.removeSurrounding("<", ">")

		val candidate: String = when {
			// 1. CID location: "cid:css-..."
			!rawLoc.isNullOrEmpty() && rawLoc.startsWith("cid:", ignoreCase = true) -> {
				val id = rawLoc.substringAfter("cid:").substringBefore('@')
				val safeId = id.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")
				if (safeId.startsWith("css-", ignoreCase = true) || part.mediaType == "text/css") {
					"styles/$safeId.css"
				} else if (part.mediaType.startsWith("image/")) {
					"images/$safeId.$extFromMime"
				} else {
					"resources/$safeId.$extFromMime"
				}
			}

			// 2. Browser extensions: "chrome-extension://..."
			!rawLoc.isNullOrEmpty() && rawLoc.startsWith("chrome-extension://", ignoreCase = true) -> {
				val afterScheme = rawLoc.substringAfter("chrome-extension://")
				val pathPart = afterScheme.substringAfter('/')
				val safe = pathPart.replace("?", "_").replace(Regex("""[^a-zA-Z0-9._/@% -]"""), "_")
				"extension/$safe"
			}

			// 3. Normal URL or image proxy
			!rawLoc.isNullOrEmpty() -> {
				val targetLoc = if (rawLoc.contains("://") && rawLoc.indexOf("://") != rawLoc.lastIndexOf("://")) {
					val lastIdx = rawLoc.lastIndexOf("://")
					rawLoc.substring(lastIdx - 4) // e.g. "https://..."
				} else {
					rawLoc
				}

				val cleanLoc = targetLoc.substringBefore('?').substringBefore('#')
				val pathPart = if (cleanLoc.contains("://")) {
					val afterScheme = cleanLoc.substringAfter("://")
					afterScheme.substringAfter('/', "")
				} else {
					cleanLoc.removePrefix("/")
				}

				if (targetLoc.contains("fonts.googleapis.com", ignoreCase = true)) {
					val family = extractParam(targetLoc, "family")?.substringBefore('&')?.substringBefore(':')
						?.replace('+', '_')?.replace(Regex("""[^a-zA-Z0-9_-]"""), "")
					if (!family.isNullOrEmpty()) {
						"fonts/$family.css"
					} else {
						"fonts/font_$index.css"
					}
				} else {
					val name = pathPart.trim('/').ifEmpty { "resource_$index" }
					val safePath = name.split('/').joinToString("/") { seg ->
						seg.replace(Regex("""[^a-zA-Z0-9._@% -]"""), "_")
					}
					if (safePath.contains('.')) safePath else "$safePath.$extFromMime"
				}
			}

			// 4. Content-ID only
			!rawCid.isNullOrEmpty() -> {
				val id = rawCid.substringBefore('@').replace(Regex("""[^a-zA-Z0-9_-]"""), "_")
				if (id.startsWith("css-", ignoreCase = true) || part.mediaType == "text/css") {
					"styles/$id.css"
				} else if (part.mediaType.startsWith("image/")) {
					"images/$id.$extFromMime"
				} else {
					"resources/$id.$extFromMime"
				}
			}

			else -> "part_$index.$extFromMime"
		}

		var finalPath = candidate
		var counter = 2
		while (assigned.contains(finalPath)) {
			val base = candidate.substringBeforeLast('.')
			val ext = candidate.substringAfterLast('.', "")
			finalPath = if (ext.isNotEmpty()) "${base}_$counter.$ext" else "${base}_$counter"
			counter++
		}
		return finalPath
	}

	private class MhtmlMemoryContent(
		override val path: String,
		override val ext: String,
		val bytes: ByteArray,
		override val mimeType: String? = null
	) : Content {
		override val length: Long = bytes.size.toLong()
		override val lastModified: Long = 0L

		override fun createInputStream(): InputStream {
			return ByteArrayInputStream(bytes)
		}

		override fun toString(): String = "$path $ext $length"
	}

	private class MhtmlStreamContent(
		val src: Source,
		override val path: String,
		override val ext: String,
		val payloadStartOffset: Long,
		val payloadRawLength: Long,
		override val length: Long,
		val transferEncoding: String,
		override val mimeType: String? = null
	) : Content {
		override val lastModified: Long = 0L

		override fun createInputStream(): InputStream {
			val raw = src.createInputStream()
			raw.skipFullBytes(payloadStartOffset)
			val bounded = BoundedInputStream(raw, payloadRawLength)
			return when (transferEncoding) {
				"base64" -> Base64.getMimeDecoder().wrap(bounded)
				"quoted-printable" -> QuotedPrintableInputStream(bounded)
				else -> bounded
			}
		}

		override fun toString(): String = "$path $ext $length"
	}

	companion object {
		private fun isHex(c: Int): Boolean {
			val ch = c.toChar()
			return ch in '0'..'9' || ch in 'A'..'F' || ch in 'a'..'f'
		}

		private fun readHeaders(cis: CountingInputStream): Map<String, String> {
			val headers = LinkedHashMap<String, String>()
			var currentKey: String? = null
			val currentVal = StringBuilder()

			fun commit() {
				val key = currentKey
				if (key != null) {
					val prev = headers[key]
					val v = currentVal.toString().trim()
					headers[key] = if (prev != null) "$prev; $v" else v
					currentKey = null
					currentVal.clear()
				}
			}

			while (true) {
				val line = readAsciiLine(cis) ?: break
				if (line.isEmpty()) {
					commit()
					break
				}
				if (line.startsWith(" ") || line.startsWith("\t")) {
					currentVal.append(" ").append(line.trim())
				} else {
					commit()
					val colon = line.indexOf(':')
					if (colon > 0) {
						currentKey = line.substring(0, colon).trim().lowercase(Locale.ROOT)
						currentVal.append(line.substring(colon + 1).trim())
					}
				}
			}
			return headers
		}

		private fun readAsciiLine(cis: CountingInputStream): String? {
			val sb = StringBuilder()
			var b = cis.read()
			if (b == -1) return null
			while (b != -1) {
				if (b == '\n'.code) break
				if (b != '\r'.code) {
					sb.append(b.toChar())
				}
				b = cis.read()
			}
			return sb.toString()
		}

		private fun extractBoundary(contentType: String): String? {
			return extractParam(contentType, "boundary")
		}

		private fun extractParam(header: String, paramName: String): String? {
			val regex = Regex("""(?i)\b$paramName\s*=\s*(?:"([^"]+)"|([^\s;]+))""")
			val match = regex.find(header) ?: return null
			return match.groupValues[1].ifEmpty { match.groupValues[2] }
		}
	}
}
