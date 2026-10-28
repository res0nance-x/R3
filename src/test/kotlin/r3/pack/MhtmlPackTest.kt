package r3.pack

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import r3.source.BinarySink
import r3.source.BinarySource
import java.util.*

class MhtmlPackTest {

	private fun samplePngBytes(): ByteArray {
		// A valid 1x1 transparent PNG
		return Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
		)
	}

	private fun createSampleMhtml(): String {
		val pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
		return """
From: <Saved by Blink>
Snapshot-Content-Location: https://example.com/news/article
Subject: Test Article Title
Date: Tue, 27 Oct 2026 15:30:00 -0000
MIME-Version: 1.0
Content-Type: multipart/related;
	type="text/html";
	boundary="----MultipartBoundary--xyz123----"

------MultipartBoundary--xyz123----
Content-Type: text/html; charset="utf-8"
Content-ID: <frame-root@mhtml.blink>
Content-Transfer-Encoding: quoted-printable
Content-Location: https://example.com/news/article

<!DOCTYPE html>
<html>
<head>
<base href="https://example.com/news/">
<link rel="stylesheet" type="text/css" href="https://example.com/static/style.css">
<link rel="stylesheet" type="text/css" href="cid:css-dynamic@mhtml.blink">
</head>
<body>
<h1>Hello=20World=21</h1>
<p>This is a test article with an image:</p>
<img src="https://example.com/media/logo.png?itok=tok123" alt="Logo">
<img src="cid:avatar@example.com" alt="Avatar">
</body>
</html>
------MultipartBoundary--xyz123----
Content-Type: text/css
Content-Transfer-Encoding: quoted-printable
Content-Location: https://example.com/static/style.css

body { color: =23333333; font-family: sans-serif; }
------MultipartBoundary--xyz123----
Content-Type: text/css
Content-Location: cid:css-dynamic@mhtml.blink
Content-Transfer-Encoding: quoted-printable

h1 { font-size: 24px; }
------MultipartBoundary--xyz123----
Content-Type: image/png
Content-Transfer-Encoding: base64
Content-Location: https://example.com/media/logo.png

$pngBase64
------MultipartBoundary--xyz123----
Content-Type: image/png
Content-ID: <avatar@example.com>
Content-Transfer-Encoding: base64

$pngBase64
------MultipartBoundary--xyz123------
""".trimIndent()
	}

	@Test
	fun testMhtmlPackIndexingAndStreaming() {
		val mhtmlStr = createSampleMhtml()
		val src = BinarySource(mhtmlStr.toByteArray(Charsets.UTF_8))
		val pack = MhtmlPack(src, rewriteLinks = true)

		assertEquals("Test Article Title", pack.title)
		assertEquals("https://example.com/news/article", pack.snapshotLocation)
		assertEquals(5, pack.size)

		// 1. Verify root index.html
		val index = pack["index.html"]
		assertNotNull(index)
		val html = index!!.createInputStream().readAllBytes().toString(Charsets.UTF_8)
		assertFalse(html.contains("<base"), "Base tag should be removed for offline relative paths")
		assertTrue(html.contains("""type="text/css""""), "type=\"text/css\" must not be corrupted to textcss.css")
		assertTrue(html.contains("""href="static/style.css""""), "CSS link should be rewritten to relative path")
		assertTrue(html.contains("""href="styles/css-dynamic.css""""), "Dynamic CID CSS link should be rewritten to relative path")
		assertTrue(html.contains("""src="media/logo.png""""), "Image link with ?itok= query string should be rewritten to relative path")
		assertTrue(html.contains("""src="images/avatar.png""""), "CID image link should be rewritten to relative path")
		assertTrue(html.contains("<h1>Hello World!</h1>"), "Quoted-printable HTML should be decoded")

		// 2. Verify static/style.css
		val css = pack["static/style.css"]
		assertNotNull(css)
		val cssContent = css!!.createInputStream().readAllBytes().toString(Charsets.UTF_8)
		assertEquals("body { color: #333333; font-family: sans-serif; }", cssContent.trim())

		// 3. Verify styles/css-dynamic.css
		val dynamicCss = pack["styles/css-dynamic.css"]
		assertNotNull(dynamicCss)
		val dynamicCssContent = dynamicCss!!.createInputStream().readAllBytes().toString(Charsets.UTF_8)
		assertEquals("h1 { font-size: 24px; }", dynamicCssContent.trim())

		// 4. Verify media/logo.png
		val logo = pack["media/logo.png"]
		assertNotNull(logo)
		val expectedPng = samplePngBytes()
		assertEquals(expectedPng.size.toLong(), logo!!.length)
		val actualPng = logo.createInputStream().readAllBytes()
		assertArrayEquals(expectedPng, actualPng)

		// 5. Verify query by original location
		val byLoc = pack.getByLocation("https://example.com/media/logo.png")
		assertNotNull(byLoc)
		assertEquals("media/logo.png", byLoc!!.path)
	}

	@Test
	fun testMhtmlWithoutLinkRewriting() {
		val mhtmlStr = createSampleMhtml()
		val src = BinarySource(mhtmlStr.toByteArray(Charsets.UTF_8))
		val pack = MhtmlPack(src, rewriteLinks = false)

		val index = pack["index.html"]
		assertNotNull(index)
		val html = index!!.createInputStream().readAllBytes().toString(Charsets.UTF_8)
		assertTrue(html.contains("""href="https://example.com/static/style.css""""), "Links should remain untouched")
		assertTrue(html.contains("""<base href="https://example.com/news/">"""), "Base tag should remain untouched")
	}

	@Test
	fun testMhtmlToBinaryPackConversion() {
		val mhtmlStr = createSampleMhtml()
		val mhtmlPack = MhtmlPack(BinarySource(mhtmlStr.toByteArray(Charsets.UTF_8)), rewriteLinks = true)

		// Convert in-memory via BinaryPack.create
		val sink = BinarySink()
		BinaryPack.create(mhtmlPack, sink)

		val binaryPackSource = BinarySource(sink.baos.toByteArray())
		val binaryPack = BinaryPack(binaryPackSource)

		val binaryMap = PackMap(binaryPack)
		assertEquals(5, binaryMap.size)

		val indexContent = binaryMap["index.html"]
		assertNotNull(indexContent)
		val html = indexContent!!.createInputStream().readAllBytes().toString(Charsets.UTF_8)
		assertTrue(html.contains("""href="static/style.css""""))

		val logoContent = binaryMap["media/logo.png"]
		assertNotNull(logoContent)
		assertArrayEquals(samplePngBytes(), logoContent!!.createInputStream().readAllBytes())
	}

	@Test
	fun testMhtmlStreamSkipping() {
		val mhtmlStr = createSampleMhtml()
		val pack = MhtmlPack(BinarySource(mhtmlStr.toByteArray(Charsets.UTF_8)), rewriteLinks = false)

		// 1. Base64 stream skipping (PNG image)
		val logo = pack["media/logo.png"]!!
		val fullPng = samplePngBytes()
		assertEquals(fullPng.size.toLong(), logo.length)

		logo.createInputStream().use { stream ->
			val skipped = stream.skip(16)
			assertEquals(16L, skipped)
			val remaining = stream.readAllBytes()
			assertArrayEquals(fullPng.copyOfRange(16, fullPng.size), remaining)
		}

		// 2. Quoted-Printable stream skipping (CSS)
		val css = pack["static/style.css"]!!
		val fullCss = "body { color: #333333; font-family: sans-serif; }\r\n"
		css.createInputStream().use { stream ->
			val skipped = stream.skip(7)
			assertEquals(7L, skipped)
			val remaining = stream.readAllBytes().toString(Charsets.UTF_8)
			assertEquals(fullCss.substring(7).trim(), remaining.trim())
		}
	}
}
