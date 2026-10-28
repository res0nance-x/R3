package r3.io

import java.io.InputStream

/**
 * A streaming decoder for Quoted-Printable encoded data (RFC 2045).
 * Decodes `=XX` hex escapes and strips soft line breaks (`=\r\n`, `=\n`).
 */
class QuotedPrintableInputStream(private val istream: InputStream) : InputStream() {
	private val pushbackBuf = IntArray(8)
	private var pushbackCount = 0

	private fun push(b: Int) {
		if (b != -1 && pushbackCount < pushbackBuf.size) {
			pushbackBuf[pushbackCount++] = b
		}
	}

	private fun pop(): Int {
		return if (pushbackCount > 0) pushbackBuf[--pushbackCount] else -1
	}

	override fun read(): Int {
		val p = pop()
		if (p != -1) {
			return p
		}

		while (true) {
			val b = istream.read()
			if (b == -1 || b != '='.code) {
				return b
			}

			// We encountered '='
			val c1 = istream.read()
			if (c1 == -1) {
				return '='.code
			}

			// Check for soft line breaks: "=\r\n" or "=\n"
			if (c1 == '\r'.code) {
				val c2 = istream.read()
				if (c2 == '\n'.code) {
					// Soft line break: ignore both and continue reading
					continue
				} else if (c2 == -1) {
					push(c1)
					return '='.code
				} else {
					// Soft break with just \r or malformed sequence
					// If standard Mac soft break "=\r", skip. Otherwise push back.
					push(c2)
					continue
				}
			} else if (c1 == '\n'.code) {
				// Soft line break: "=\n"
				continue
			}

			// Check for hex escape: "=XX"
			val c2 = istream.read()
			if (c2 == -1) {
				push(c1)
				return '='.code
			}

			val v1 = hexDigit(c1)
			val v2 = hexDigit(c2)
			if (v1 != -1 && v2 != -1) {
				return (v1 shl 4) or v2
			} else {
				// Not a valid hex escape. Push back c2 then c1, and return '='
				push(c2)
				push(c1)
				return '='.code
			}
		}
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		if (off < 0 || len < 0 || len > b.size - off) {
			throw IndexOutOfBoundsException()
		}
		if (len == 0) return 0
		var bytesRead = 0
		for (i in 0 until len) {
			val next = read()
			if (next == -1) {
				return if (bytesRead == 0) -1 else bytesRead
			}
			b[off + i] = next.toByte()
			bytesRead++
		}
		return bytesRead
	}

	override fun skip(n: Long): Long {
		if (n <= 0) return 0L
		var remaining = n
		val buf = ByteArray(minOf(remaining, 8192L).toInt())
		while (remaining > 0) {
			val toRead = minOf(remaining, buf.size.toLong()).toInt()
			val read = read(buf, 0, toRead)
			if (read <= 0) break
			remaining -= read
		}
		return n - remaining
	}

	override fun available(): Int {
		return pushbackCount + istream.available()
	}

	override fun close() {
		istream.close()
	}

	companion object {
		fun hexDigit(c: Int): Int {
			return when (c.toChar()) {
				in '0'..'9' -> c - '0'.code
				in 'A'..'F' -> c - 'A'.code + 10
				in 'a'..'f' -> c - 'a'.code + 10
				else -> -1
			}
		}
	}
}
