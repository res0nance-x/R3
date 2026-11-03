package r3.pack

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import r3.content.Content
import r3.content.getMimeType
import r3.io.log
import r3.source.BinarySource
import java.io.ByteArrayInputStream
import java.io.InputStream

class MHTPackMapTest {

	private class SimpleContent(
		override val path: String,
		override val ext: String = path.substringAfterLast('.', ""),
		val data: String = "content of $path",
		override val mimeType: String? = null
	) : Content {
		override val length: Long = data.length.toLong()
		override val lastModified: Long = 1000L
		override fun createInputStream(): InputStream = ByteArrayInputStream(data.toByteArray())
	}

	@Test
	fun testPackMapOriginalExactOnly() {
		val list = listOf(
			SimpleContent("media/logo.png"),
			SimpleContent("style.css")
		)
		val packMap = PackMap(list)

		// Exact match works
		assertNotNull(packMap["media/logo.png"])
		assertNotNull(packMap["style.css"])

		// Loose matching must NOT work in original PackMap
		assertNull(packMap["media/LOGO.PNG"])
		assertNull(packMap["logo.png"])
		assertNull(packMap["https://example.com/media/logo.png"])
		assertNull(packMap["media/logo.png?v=123"])
		assertNull(packMap["media/logo%20v2.png"])
	}

	@Test
	fun testMHTPackMapPathOnly() {
		val item = SimpleContent("media/banner.jpg")
		val map = MHTPackMap(listOf(item))

		// Strips scheme and host
		assertNotNull(map["https://example.com/media/banner.jpg"])
		assertNotNull(map["http://other-domain.org/media/banner.jpg"])
		assertNotNull(map["//cdn.example.com/media/banner.jpg"])

		// Strips query parameters and fragments
		assertNotNull(map["https://example.com/media/banner.jpg?size=large&v=2#details"])
		assertNotNull(map["media/banner.jpg?quality=80"])
		assertNotNull(map["/media/banner.jpg"])
	}

	@Test
	fun testMHTPackMapPercentEncoding() {
		// Entry stored with %20
		val itemWithPercent = SimpleContent("files/my%20document%2B1.pdf")
		val map1 = MHTPackMap(listOf(itemWithPercent))

		// Query without percent (space)
		assertNotNull(map1["files/my document 1.pdf"])
		// Query with nothing
		assertNotNull(map1["files/mydocument1.pdf"])
		// Query exact
		assertNotNull(map1["files/my%20document%2B1.pdf"])

		// Entry stored with spaces
		val itemWithSpace = SimpleContent("files/annual report.pdf")
		val map2 = MHTPackMap(listOf(itemWithSpace))

		// Query with percent encoding
		assertNotNull(map2["files/annual%20report.pdf"])
		// Query with nothing
		assertNotNull(map2["files/annualreport.pdf"])
	}

	@Test
	fun testMHTPackMapCaseInsensitive() {
		val item = SimpleContent("Images/Photos/Sample.PNG")
		val map = MHTPackMap(listOf(item))

		assertNotNull(map["images/photos/sample.png"])
		assertNotNull(map["IMAGES/PHOTOS/SAMPLE.PNG"])
		assertNotNull(map["Images/Photos/Sample.PNG"])
	}

	@Test
	fun testMHTPackMapTruncatedPath() {
		val item = SimpleContent("static/assets/css/theme.css")
		val map = MHTPackMap(listOf(item))

		// Progressively truncated requests from deeper paths
		assertNotNull(map["https://example.com/news/article/static/assets/css/theme.css"])
		assertNotNull(map["assets/css/theme.css"])
		assertNotNull(map["css/theme.css"])
		assertNotNull(map["theme.css"])
	}

	@Test
	fun testMHTPackMapFilenameOnlyAndScoring() {
		val item1 = SimpleContent("news/header.jpg")
		val item2 = SimpleContent("sports/header.jpg")
		val map = MHTPackMap(listOf(item1, item2))

		// Resolves by filename only
		val match1 = map["https://cdn.com/archive/2026/sports/header.jpg"]
		assertNotNull(match1)
		assertEquals("sports/header.jpg", match1?.path)

		val match2 = map["https://cdn.com/archive/2026/news/header.jpg"]
		assertNotNull(match2)
		assertEquals("news/header.jpg", match2?.path)
	}

	@Test
	fun testMHTPackMapWarningOutput() {
		val warnings = mutableListOf<String>()
		val origLog = log
		try {
			log = { msg -> warnings.add(msg) }

			val item = SimpleContent("assets/image.png")
			val map = MHTPackMap(listOf(item))

			// Exact match -> no warning
			val exact = map["assets/image.png"]
			assertNotNull(exact)
			assertTrue(warnings.isEmpty(), "Exact match should not log a warning")

			// Loose match by filename -> emits warning
			val loose = map["https://example.com/other/path/image.png?v=1"]
			assertNotNull(loose)
			assertEquals(1, warnings.size)
			assertTrue(warnings[0].contains("MHTPackMap WARNING"), "Should contain warning prefix")
			assertTrue(warnings[0].contains("assets/image.png"), "Should name the matched path")
		} finally {
			log = origLog
		}
	}

	@Test
	fun testMHTPackMapMimeTypePreservation() {
		val customMime = "image/avif"
		// Path says .jpg, but MHTML header declared it as image/avif
		val item = SimpleContent("photos/pic.jpg", ext = "jpg", mimeType = customMime)
		val map = MHTPackMap(listOf(item))

		val resolved = map["pic.jpg"]
		assertNotNull(resolved)
		assertEquals(customMime, resolved?.mimeType)
		assertEquals(customMime, resolved?.getMimeType())
	}

	@Test
	fun testMHTPackMapContentId() {
		val item = SimpleContent("styles/css-12345.css")
		val map = MHTPackMap(listOf(item))

		val resolved = map["cid:css-12345@mhtml.blink"]
		assertNotNull(resolved)
		assertEquals("styles/css-12345.css", resolved?.path)
	}
}

