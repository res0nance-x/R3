package r3.net.tcp

import r3.content.Content
import r3.content.ContentMeta
import r3.pack.Pack
import java.io.IOException
import java.io.InputStream

/**
 * Stateless client interface used by [TCPPack], [TCPContent], and [TCPInputStream]
 * to list remote content metadata and read byte chunks by (path, position).
 */
interface TCPPackClient {
	fun listContent(): List<ContentMeta>
	fun readChunk(path: String, pos: Long, maxLength: Int = TCPInputStream.DEFAULT_CHUNK_SIZE): ByteArray
}

/**
 * Smart [InputStream] that defers network reads until [read] is called and handles
 * [skip] purely by updating its local [pos] offset (enabling O(1) zero-network HTTP Range seeking).
 * Reads request up to [chunkSize] bytes (default 64 KB) from [client] and buffer them locally.
 */
class TCPInputStream(
	val path: String,
	val length: Long,
	val client: TCPPackClient,
	val chunkSize: Int = DEFAULT_CHUNK_SIZE
) : InputStream() {
	companion object {
		const val DEFAULT_CHUNK_SIZE: Int = 65536
	}

	private var pos: Long = 0L
	private var buf: ByteArray = ByteArray(0)
	private var bufStartPos: Long = 0L
	private var closed: Boolean = false

	override fun read(): Int {
		val single = ByteArray(1)
		val n = read(single, 0, 1)
		return if (n <= 0) -1 else (single[0].toInt() and 0xFF)
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		if (off < 0 || len < 0 || len > b.size - off) {
			throw IndexOutOfBoundsException()
		}
		if (len == 0) return 0
		if (closed) throw IOException("Stream closed")
		if (pos >= length) return -1

		// Check if current pos falls within our buffered chunk [bufStartPos, bufStartPos + buf.size)
		val bufEndPos = bufStartPos + buf.size
		if (buf.isEmpty() || pos < bufStartPos || pos >= bufEndPos) {
			val remainingInFile = (length - pos).coerceAtMost(chunkSize.toLong()).toInt()
			if (remainingInFile <= 0) return -1
			val fetched = client.readChunk(path, pos, chunkSize)
			if (fetched.isEmpty()) return -1
			buf = fetched
			bufStartPos = pos
		}

		val offsetInBuf = (pos - bufStartPos).toInt()
		val availableInBuf = buf.size - offsetInBuf
		if (availableInBuf <= 0) return -1

		val toCopy = minOf(len, availableInBuf, (length - pos).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
		if (toCopy <= 0) return -1

		System.arraycopy(buf, offsetInBuf, b, off, toCopy)
		pos += toCopy
		return toCopy
	}

	override fun skip(n: Long): Long {
		if (closed) throw IOException("Stream closed")
		if (n <= 0L) return 0L
		val remaining = (length - pos).coerceAtLeast(0L)
		val actualSkip = minOf(n, remaining)
		pos += actualSkip
		return actualSkip
	}

	override fun available(): Int {
		if (closed || pos >= length) return 0
		val bufEndPos = bufStartPos + buf.size
		return if (pos in bufStartPos until bufEndPos) {
			(bufEndPos - pos).toInt()
		} else {
			0
		}
	}

	override fun close() {
		closed = true
		buf = ByteArray(0)
	}
}

/**
 * [Content] backed by a remote node via [TCPPackClient].
 * Opening an [InputStream] via [createInputStream] returns a [TCPInputStream] that only
 * requests chunks over the network when [InputStream.read] is actually invoked.
 */
class TCPContent(
	val meta: ContentMeta,
	val client: TCPPackClient
) : Content {
	override val path: String
		get() = meta.name
	override val ext: String
		get() = meta.type
	override val length: Long
		get() = meta.length
	override val lastModified: Long
		get() = meta.created

	override fun createInputStream(): InputStream {
		return TCPInputStream(path, length, client)
	}
}

/**
 * [Pack] adaptation over a remote node connection using [TCPPackClient].
 */
class TCPPack(
	val client: TCPPackClient
) : Pack {
	override fun iterator(): Iterator<Content> {
		return client.listContent().map { TCPContent(it, client) }.iterator()
	}
}
