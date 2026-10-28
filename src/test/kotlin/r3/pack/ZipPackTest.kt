package r3.pack

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import r3.source.BinarySource
import r3.source.FileSource
import r3.source.readString
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipPackTest {

	private fun createSampleZip(entries: Map<String, String>): ByteArray {
		val baos = ByteArrayOutputStream()
		ZipOutputStream(baos).use { zos ->
			for ((name, content) in entries) {
				val entry = ZipEntry(name)
				zos.putNextEntry(entry)
				zos.write(content.toByteArray(Charsets.UTF_8))
				zos.closeEntry()
			}
		}
		return baos.toByteArray()
	}

	@Test
	fun testZipPackWithFlatEntries() {
		val zipBytes = createSampleZip(
			mapOf(
				"index.html" to "<h1>Home</h1>",
				"style.css" to "body { color: red; }",
				"sub/data.json" to "{\"key\": \"val\"}"
			)
		)

		val src = BinarySource(zipBytes)
		ZipPack(src).use { pack ->
			assertEquals(3, pack.size)
			assertNotNull(pack["index.html"])
			assertNotNull(pack["style.css"])
			assertNotNull(pack["sub/data.json"])
			assertEquals("<h1>Home</h1>", pack["index.html"]!!.readString())
			assertEquals("body { color: red; }", pack["style.css"]!!.readString())
			assertEquals("html", pack["index.html"]!!.ext)
			assertEquals("css", pack["style.css"]!!.ext)
			assertEquals("json", pack["sub/data.json"]!!.ext)
		}
	}

	@Test
	fun testZipPackWithCommonRootDirectory() {
		// Simulates zipping a directory "my-site"
		val zipBytes = createSampleZip(
			mapOf(
				"my-site/index.html" to "<h1>Home</h1>",
				"my-site/css/style.css" to "body { margin: 0; }",
				"my-site/images/logo.txt" to "Logo content"
			)
		)

		val temp = File.createTempFile("test_site_", ".zip")
		temp.deleteOnExit()
		temp.writeBytes(zipBytes)

		// With stripCommonRoot = true (default, consistent with DirPack)
		ZipPack(temp).use { pack ->
			assertEquals(3, pack.size)
			assertNotNull(pack["index.html"])
			assertNotNull(pack["css/style.css"])
			assertNotNull(pack["images/logo.txt"])
			assertEquals("<h1>Home</h1>", pack["index.html"]!!.readString())
		}

		// With stripCommonRoot = false
		ZipPack(temp, stripCommonRoot = false).use { pack ->
			assertEquals(3, pack.size)
			assertNotNull(pack["my-site/index.html"])
			assertNull(pack["index.html"])
		}

		temp.delete()
	}

	@Test
	fun testZipPackFromFileSource() {
		val zipBytes = createSampleZip(
			mapOf(
				"test.txt" to "Hello World"
			)
		)
		val temp = File.createTempFile("test_file_src_", ".zip")
		temp.deleteOnExit()
		temp.writeBytes(zipBytes)

		val src = FileSource(temp)
		ZipPack(src).use { pack ->
			assertEquals(1, pack.size)
			assertEquals("Hello World", pack["test.txt"]!!.readString())
		}

		temp.delete()
	}

	@Test
	fun testZipPackSkipping() {
		val zipBytes = createSampleZip(
			mapOf("sample.txt" to "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ")
		)
		val src = BinarySource(zipBytes)
		ZipPack(src).use { pack ->
			val content = pack["sample.txt"]!!
			assertEquals(36L, content.length)

			// Verify skipping 10 decoded bytes
			content.createInputStream().use { stream ->
				val skipped = stream.skip(10)
				assertEquals(10L, skipped)
				val remaining = stream.readAllBytes().toString(Charsets.UTF_8)
				assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", remaining)
			}
		}
	}
}
