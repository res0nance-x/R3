package r3.pack

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import r3.content.BinaryContent
import r3.content.Content
import r3.encryption.EncryptedSink
import r3.encryption.EncryptedSource
import r3.hash.hash256
import r3.math.EncryptedSequence
import r3.pke.Password256
import r3.source.FileSink
import r3.source.FileSource
import java.io.File

class BinaryPackAppendTest {

	private fun Content.readText(): String {
		return createInputStream().use { it.readBytes().decodeToString() }
	}

	@Test
	fun testAppendUnencryptedPack(@TempDir tempDir: File) {
		val packFile = File(tempDir, "test.pack")
		val file1 = BinaryContent("Hello file 1".toByteArray(), "file1.txt", "txt")
		val file2 = BinaryContent("Hello file 2".toByteArray(), "file2.txt", "txt")

		// 1. Create initial pack
		BinaryPack.create(listOf(file1, file2), FileSink(packFile, append = false))

		// 2. Append additional files
		val file3 = BinaryContent("Hello file 3".toByteArray(), "dir/file3.txt", "txt")
		val file4 = BinaryContent("Hello file 4".toByteArray(), "dir/file4.txt", "txt")
		BinaryPack.append(listOf(file3, file4), FileSink(packFile, append = true))

		// 3. Read back pack
		val pack = BinaryPack(FileSource(packFile))
		val packMap = PackMap(pack)

		assertEquals(4, pack.count())
		assertEquals("Hello file 1", packMap["file1.txt"]?.readText())
		assertEquals("Hello file 2", packMap["file2.txt"]?.readText())
		assertEquals("Hello file 3", packMap["dir/file3.txt"]?.readText())
		assertEquals("Hello file 4", packMap["dir/file4.txt"]?.readText())
	}

	@Test
	fun testAppendDuplicateKeyOverridesInPackMap(@TempDir tempDir: File) {
		val packFile = File(tempDir, "test_dup.pack")
		val orig = BinaryContent("Original content".toByteArray(), "data.txt", "txt")
		BinaryPack.create(listOf(orig), FileSink(packFile, append = false))

		val updated = BinaryContent("Updated content".toByteArray(), "data.txt", "txt")
		BinaryPack.append(listOf(updated), FileSink(packFile, append = true))

		val pack = BinaryPack(FileSource(packFile))
		assertEquals(2, pack.count()) // Both entries exist in stream

		val packMap = PackMap(pack)
		assertEquals("Updated content", packMap["data.txt"]?.readText())
	}

	@Test
	fun testAppendEncryptedPack(@TempDir tempDir: File) {
		val epackFile = File(tempDir, "test.epack")
		val password = Password256("secretPass123".toByteArray().hash256())

		val file1 = BinaryContent("Encrypted content 1".toByteArray(), "doc1.txt", "txt")
		val file2 = BinaryContent("Encrypted content 2".toByteArray(), "doc2.txt", "txt")

		// 1. Create encrypted pack
		val seq1 = EncryptedSequence.createSequence(password)
		val encSink1 = EncryptedSink(seq1, FileSink(epackFile, append = false))
		BinaryPack.create(listOf(file1, file2), encSink1)

		// 2. Append to encrypted pack
		val file3 = BinaryContent("Encrypted content 3".toByteArray(), "doc3.txt", "txt")
		val initialPos = epackFile.length()
		val seq2 = EncryptedSequence.createSequence(password)
		val encSink2 = EncryptedSink(seq2, FileSink(epackFile, append = true), initialPos = initialPos)
		BinaryPack.append(listOf(file3), encSink2)

		// 3. Read back and decrypt
		val seqRead = EncryptedSequence.createSequence(password)
		val encSource = EncryptedSource(seqRead, FileSource(epackFile))
		val pack = BinaryPack(encSource)
		val packMap = PackMap(pack)

		assertEquals(3, pack.count())
		assertEquals("Encrypted content 1", packMap["doc1.txt"]?.readText())
		assertEquals("Encrypted content 2", packMap["doc2.txt"]?.readText())
		assertEquals("Encrypted content 3", packMap["doc3.txt"]?.readText())
	}
}
