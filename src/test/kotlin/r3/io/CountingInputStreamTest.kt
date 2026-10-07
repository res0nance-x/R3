package r3.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class CountingInputStreamTest {

	@Test
	fun testSingleByteRead() {
		val data = byteArrayOf(1, 2, 3, 4, 5)
		val cis = CountingInputStream(ByteArrayInputStream(data))
		assertEquals(0L, cis.count)

		assertEquals(1, cis.read())
		assertEquals(1L, cis.count)

		assertEquals(2, cis.read())
		assertEquals(2L, cis.count)
	}

	@Test
	fun testBufferReadDoesNotDoubleCount() {
		val data = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80)
		val cis = CountingInputStream(ByteArrayInputStream(data))

		val buf = ByteArray(4)
		val read1 = cis.read(buf)
		assertEquals(4, read1)
		assertEquals(4L, cis.count)
		assertEquals(10, buf[0])
		assertEquals(40, buf[3])

		val read2 = cis.read(buf, 0, 2)
		assertEquals(2, read2)
		assertEquals(6L, cis.count)
		assertEquals(50, buf[0])
		assertEquals(60, buf[1])
	}

	@Test
	fun testSkip() {
		val data = ByteArray(100) { it.toByte() }
		val cis = CountingInputStream(ByteArrayInputStream(data))

		val skipped = cis.skip(25)
		assertEquals(25L, skipped)
		assertEquals(25L, cis.count)

		val b = cis.read()
		assertEquals(25, b)
		assertEquals(26L, cis.count)
	}
}
