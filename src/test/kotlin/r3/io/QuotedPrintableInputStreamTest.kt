package r3.io

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class QuotedPrintableInputStreamTest {

	@Test
	fun testPlainTextPassThrough() {
		val input = "Hello, world! 1234567890."
		val stream = QuotedPrintableInputStream(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))
		val output = stream.readAllBytes().toString(Charsets.UTF_8)
		assertEquals(input, output)
	}

	@Test
	fun testHexEscapes() {
		val input = "Hello=20World=21=3D"
		val stream = QuotedPrintableInputStream(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))
		val output = stream.readAllBytes().toString(Charsets.UTF_8)
		assertEquals("Hello World!=", output)
	}

	@Test
	fun testSoftLineBreaks() {
		val input = "This is a very long line that was wrapped=\r\nwith a soft break and then=\nanother soft break."
		val stream = QuotedPrintableInputStream(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))
		val output = stream.readAllBytes().toString(Charsets.UTF_8)
		assertEquals("This is a very long line that was wrappedwith a soft break and thenanother soft break.", output)
	}

	@Test
	fun testUtf8MultiByteQuotedPrintable() {
		// "€" in UTF-8 is E2 82 AC
		val input = "Price: =E2=82=AC100"
		val stream = QuotedPrintableInputStream(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))
		val output = stream.readAllBytes().toString(Charsets.UTF_8)
		assertEquals("Price: €100", output)
	}

	@Test
	fun testInvalidHexPassThrough() {
		val input = "Invalid =ZZ escape and trailing ="
		val stream = QuotedPrintableInputStream(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))
		val output = stream.readAllBytes().toString(Charsets.UTF_8)
		assertEquals("Invalid =ZZ escape and trailing =", output)
	}

	@Test
	fun testBulkReadBuffer() {
		val input = "A=20B=20C=20D"
		val stream = QuotedPrintableInputStream(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))
		val buf = ByteArray(10)
		val n = stream.read(buf, 0, 7)
		assertEquals(7, n)
		assertEquals("A B C D", String(buf, 0, n, Charsets.UTF_8))
	}
}
