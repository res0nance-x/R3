package r3.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

class SkipFullBytesTest {

	@Test
	fun testSkipZeroAndNegative() {
		val data = byteArrayOf(1, 2, 3)
		val stream = ByteArrayInputStream(data)
		stream.skipFullBytes(0)
		assertEquals(1, stream.read())
		stream.skipFullBytes(-5)
		assertEquals(2, stream.read())
	}

	@Test
	fun testSkipNormal() {
		val data = ByteArray(1000) { (it and 0xFF).toByte() }
		val stream = ByteArrayInputStream(data)
		stream.skipFullBytes(250)
		assertEquals((250 and 0xFF).toByte(), stream.read().toByte())

		stream.skipFullBytes(748)
		assertEquals((999 and 0xFF).toByte(), stream.read().toByte())
		assertEquals(-1, stream.read())
	}

	@Test
	fun testSkipExactLength() {
		val data = byteArrayOf(10, 20, 30)
		val stream = ByteArrayInputStream(data)
		stream.skipFullBytes(3)
		assertEquals(-1, stream.read())
	}

	@Test
	fun testSkipPastEofThrowsEOFException() {
		val data = byteArrayOf(1, 2, 3)
		val stream = ByteArrayInputStream(data)
		val ex = assertThrows(EOFException::class.java) {
			stream.skipFullBytes(5)
		}
		assertEquals("Unexpected EOF while skipping 5 bytes (remaining: 2)", ex.message)
	}

	@Test
	fun testSkipEmptyStreamThrowsEOFException() {
		val stream = ByteArrayInputStream(byteArrayOf())
		val ex = assertThrows(EOFException::class.java) {
			stream.skipFullBytes(1)
		}
		assertEquals("Unexpected EOF while skipping 1 bytes (remaining: 1)", ex.message)
	}

	@Test
	fun testNonSkippableStreamFallbackSmall() {
		val data = ByteArray(500) { ((it + 1) and 0xFF).toByte() }
		// A stream where skip() returns 0 always (like a URL / HTTP stream)
		val stream = object : InputStream() {
			var pos = 0
			override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1
			override fun skip(n: Long): Long = 0L // Does not support skip
		}

		stream.skipFullBytes(120)
		assertEquals(data[120], stream.read().toByte())
	}

	@Test
	fun testNonSkippableStreamFallbackLargeMultiChunk() {
		val totalSize = 150_000
		val data = ByteArray(totalSize) { (it and 0xFF).toByte() }
		val stream = object : InputStream() {
			var pos = 0
			override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1
			override fun read(b: ByteArray, off: Int, len: Int): Int {
				if (pos >= data.size) return -1
				val available = data.size - pos
				val toRead = min(available, len)
				System.arraycopy(data, pos, b, off, toRead)
				pos += toRead
				return toRead
			}
			override fun skip(n: Long): Long = 0L
		}

		val skipAmount = 140_000L
		stream.skipFullBytes(skipAmount)
		assertEquals((140_000 and 0xFF).toByte(), stream.read().toByte())
	}

	@Test
	fun testPartialSkipStream() {
		val data = ByteArray(100) { it.toByte() }
		val stream = object : InputStream() {
			var pos = 0
			override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1
			override fun skip(n: Long): Long {
				val toSkip = min(min(n, 7L), (data.size - pos).toLong()).toInt()
				pos += toSkip
				return toSkip.toLong()
			}
		}

		stream.skipFullBytes(50)
		assertEquals(50.toByte(), stream.read().toByte())
	}

	@Test
	fun testZeroReadProgressFallback() {
		val data = byteArrayOf(10, 20, 30, 40)
		var returnedZeroReadOnce = false
		val stream = object : InputStream() {
			var pos = 0
			override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1
			override fun read(b: ByteArray, off: Int, len: Int): Int {
				if (!returnedZeroReadOnce) {
					returnedZeroReadOnce = true
					return 0 // Non-compliant / non-blocking stream returning 0
				}
				return super.read(b, off, len)
			}
			override fun skip(n: Long): Long = 0L
		}

		stream.skipFullBytes(3)
		assertEquals(40, stream.read())
	}

	@Test
	fun testRogueStreamNegativeSkipThrowsIOException() {
		val stream = object : InputStream() {
			override fun read(): Int = 0
			override fun skip(n: Long): Long = -1L // Rogue stream returning -1
		}

		assertThrows(IOException::class.java) {
			stream.skipFullBytes(10)
		}
	}

	@Test
	fun testRogueStreamOverSkipThrowsIOException() {
		val stream = object : InputStream() {
			override fun read(): Int = 0
			override fun skip(n: Long): Long = n + 50L // Rogue stream skipping more than requested
		}

		assertThrows(IOException::class.java) {
			stream.skipFullBytes(10)
		}
	}
}
