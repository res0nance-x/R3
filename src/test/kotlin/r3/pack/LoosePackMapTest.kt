package r3.pack

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import r3.content.BinaryContent
import r3.content.Content

class LoosePackMapTest {

	private fun content(path: String, text: String = path): Content {
		val ext = path.substringAfterLast('.', "")
		return BinaryContent(text.toByteArray(), path, ext)
	}

	@Test
	fun testStrictMatch() {
		val items = listOf(
			content("images/photo.jpg", "exact photo"),
			content("docs/readme.txt", "exact readme")
		)
		val map = LoosePackMap(items)
		assertEquals("exact photo", map["images/photo.jpg"]?.createInputStream()?.reader()?.readText())
		assertEquals("exact readme", map["docs/readme.txt"]?.createInputStream()?.reader()?.readText())
	}

	@Test
	fun testCaseInsensitiveMatch() {
		val items = listOf(
			content("images/photo.jpg", "photo data"),
			content("Docs/Nested/FILE.TXT", "file data")
		)
		val map = LoosePackMap(items)
		assertEquals("photo data", map["IMAGES/PHOTO.JPG"]?.createInputStream()?.reader()?.readText())
		assertEquals("photo data", map["Images/Photo.jpg"]?.createInputStream()?.reader()?.readText())
		assertEquals("file data", map["docs/nested/file.txt"]?.createInputStream()?.reader()?.readText())
	}

	@Test
	fun testUrlEncodingEscapesToUnderscore() {
		val items = listOf(
			content("media/cool_song_1_.mp3", "song data"),
			content("my_document.pdf", "doc data")
		)
		val map = LoosePackMap(items)
		// Request with %20 and %28 %29 escapes in rawPath or path
		assertEquals("song data", map.get("media/cool song(1).mp3", "media/cool%20song%281%29.mp3")?.createInputStream()?.reader()?.readText())
		assertEquals("doc data", map.get("my document.pdf", "my%20document.pdf")?.createInputStream()?.reader()?.readText())
		assertEquals("doc data", map["my%20document.pdf"]?.createInputStream()?.reader()?.readText())
	}

	@Test
	fun testUrlEncodingEscapesRemoved() {
		val items = listOf(
			content("files/document1.pdf", "doc1 data"),
			content("photo.jpg", "photo data")
		)
		val map = LoosePackMap(items)
		// Request with %20 or %28%29 removed
		assertEquals("doc1 data", map.get("files/document(1).pdf", "files/document%281%29.pdf")?.createInputStream()?.reader()?.readText())
		assertEquals("photo data", map.get("photo.jpg", "photo%20.jpg")?.createInputStream()?.reader()?.readText())
	}

	@Test
	fun testPathRobustMissingStartPath() {
		val items = listOf(
			content("ArchiveRoot/subfolder/assets/logo.png", "logo data")
		)
		val map = LoosePackMap(items)
		// URL is missing ArchiveRoot/ or ArchiveRoot/subfolder/
		assertEquals("logo data", map["subfolder/assets/logo.png"]?.createInputStream()?.reader()?.readText())
		assertEquals("logo data", map["assets/logo.png"]?.createInputStream()?.reader()?.readText())
	}

	@Test
	fun testPathRobustExtraStartPath() {
		val items = listOf(
			content("assets/logo.png", "logo data")
		)
		val map = LoosePackMap(items)
		// URL has extra start path /site/v1/
		assertEquals("logo data", map["site/v1/assets/logo.png"]?.createInputStream()?.reader()?.readText())
	}

	@Test
	fun testSimpleFilenameMatch() {
		val items = listOf(
			content("somewhere/deep/inside/unique_media.mp4", "media data")
		)
		val map = LoosePackMap(items)
		// Totally different folder structure, matching filename
		assertEquals("media data", map["wrong/path/unique_media.mp4"]?.createInputStream()?.reader()?.readText())
		assertEquals("media data", map["unique_media.mp4"]?.createInputStream()?.reader()?.readText())
		// Also works with case insensitivity and escape conversion
		assertEquals("media data", map.get("other/UNIQUE MEDIA.MP4", "other/UNIQUE%20MEDIA.MP4")?.createInputStream()?.reader()?.readText())
	}

	@Test
	fun testPriorityOrderStrictOverLoose() {
		val items = listOf(
			content("images/photo.jpg", "exact match"),
			content("other/images/photo.jpg", "other match"),
			content("IMAGES/PHOTO.JPG", "uppercase match")
		)
		val map = LoosePackMap(items)
		// Exact match takes priority
		assertEquals("exact match", map["images/photo.jpg"]?.createInputStream()?.reader()?.readText())
		assertEquals("uppercase match", map["IMAGES/PHOTO.JPG"]?.createInputStream()?.reader()?.readText())
	}

	@Test
	fun testNonExistentReturnsNull() {
		val items = listOf(
			content("images/photo.jpg", "photo data")
		)
		val map = LoosePackMap(items)
		assertNull(map["images/notfound.jpg"])
		assertNull(map["photo.png"]) // different extension
	}

	@Test
	fun testQueryParametersAndFragments() {
		val items = listOf(
			content("css/style.css", "css data"),
			content("page.html", "html data")
		)
		val map = LoosePackMap(items)
		assertEquals("css data", map["css/style.css?v=2.1.0"]?.createInputStream()?.reader()?.readText())
		assertEquals("html data", map["page.html#section-heading"]?.createInputStream()?.reader()?.readText())
		assertEquals("css data", map["/css/style.css?v=1#hash"]?.createInputStream()?.reader()?.readText())
	}

	@Test
	fun testWindowsBackslashSeparators() {
		val items = listOf(
			content("assets/images/banner.png", "banner data")
		)
		val map = LoosePackMap(items)
		assertEquals("banner data", map["assets\\images\\banner.png"]?.createInputStream()?.reader()?.readText())
		assertEquals("banner data", map["\\assets\\images\\banner.png"]?.createInputStream()?.reader()?.readText())
	}

	@Test
	fun testEquivalentExtensionsHtmAndHtml() {
		val items = listOf(
			content("docs/manual.htm", "manual data"),
			content("gallery/photo.jpeg", "photo data")
		)
		val map = LoosePackMap(items)
		// .htm matches .html request and vice versa
		assertEquals("manual data", map["docs/manual.html"]?.createInputStream()?.reader()?.readText())
		assertEquals("photo data", map["gallery/photo.jpg"]?.createInputStream()?.reader()?.readText())
	}

	@Test
	fun testIndexFallbackAndBlankPath() {
		val itemsNested = listOf(
			content("MyArchive/sub/index.html", "archive home")
		)
		val map1 = LoosePackMap(itemsNested)
		assertEquals("archive home", map1[""]?.createInputStream()?.reader()?.readText())
		assertEquals("archive home", map1["/"]?.createInputStream()?.reader()?.readText())
		assertEquals("archive home", map1["index.html"]?.createInputStream()?.reader()?.readText())

		val itemsHtm = listOf(
			content("default.htm", "htm home")
		)
		val map2 = LoosePackMap(itemsHtm)
		assertEquals("htm home", map2["index.html"]?.createInputStream()?.reader()?.readText())

		val itemsSingleHtml = listOf(
			content("presentation/article.html", "article content")
		)
		val map3 = LoosePackMap(itemsSingleHtml)
		assertEquals("article content", map3["index.html"]?.createInputStream()?.reader()?.readText())
		assertEquals("article content", map3[""]?.createInputStream()?.reader()?.readText())
	}
}
