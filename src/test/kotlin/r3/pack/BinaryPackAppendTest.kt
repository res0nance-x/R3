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

	@Test
	fun testDirectConcatenation(@TempDir tempDir: File) {
		val packFile1 = File(tempDir, "pack1.pack")
		val packFile2 = File(tempDir, "pack2.pack")
		val combinedFile = File(tempDir, "combined.pack")

		val file1 = BinaryContent("Content 1".toByteArray(), "c1.txt", "txt")
		val file2 = BinaryContent("Content 2".toByteArray(), "c2.txt", "txt")
		BinaryPack.create(listOf(file1, file2), FileSink(packFile1))

		val file3 = BinaryContent("Content 3".toByteArray(), "c3.txt", "txt")
		val file4 = BinaryContent("Content 4".toByteArray(), "c4.txt", "txt")
		BinaryPack.create(listOf(file3, file4), FileSink(packFile2))

		// Directly concatenate the two pack files (cat pack1 pack2 > combined)
		combinedFile.outputStream().use { out ->
			packFile1.inputStream().use { it.copyTo(out) }
			packFile2.inputStream().use { it.copyTo(out) }
		}

		val combinedPack = BinaryPack(FileSource(combinedFile))
		val packMap = PackMap(combinedPack)

		assertEquals(4, combinedPack.count())
		assertEquals("Content 1", packMap["c1.txt"]?.readText())
		assertEquals("Content 2", packMap["c2.txt"]?.readText())
		assertEquals("Content 3", packMap["c3.txt"]?.readText())
		assertEquals("Content 4", packMap["c4.txt"]?.readText())
	}

	@Test
	fun testDeleteAndCompact(@TempDir tempDir: File) {
		val packFile = File(tempDir, "deletable.pack")
		val file1 = BinaryContent("File 1 content".toByteArray(), "f1.txt", "txt")
		val file2 = BinaryContent("File 2 to be deleted".toByteArray(), "f2.txt", "txt")
		val file3 = BinaryContent("File 3 content".toByteArray(), "f3.txt", "txt")

		BinaryPack.create(listOf(file1, file2, file3), FileSink(packFile, append = false))

		// Delete f2.txt by appending a tombstone index
		BinaryPack.delete(listOf("f2.txt"), FileSink(packFile, append = true))

		val packAfterDelete = BinaryPack(FileSource(packFile))
		val mapAfterDelete = PackMap(packAfterDelete)

		assertEquals(2, packAfterDelete.count())
		assertEquals("File 1 content", mapAfterDelete["f1.txt"]?.readText())
		assertEquals("File 3 content", mapAfterDelete["f3.txt"]?.readText())
		org.junit.jupiter.api.Assertions.assertNull(mapAfterDelete["f2.txt"])

		// Compact the file to remove dead payload data
		val compactedFile = File(tempDir, "compacted.pack")
		BinaryPack.compact(FileSource(packFile), FileSink(compactedFile, append = false))

		val compactedPack = BinaryPack(FileSource(compactedFile))
		val compactedMap = PackMap(compactedPack)

		assertEquals(2, compactedPack.count())
		assertEquals("File 1 content", compactedMap["f1.txt"]?.readText())
		assertEquals("File 3 content", compactedMap["f3.txt"]?.readText())
		org.junit.jupiter.api.Assertions.assertNull(compactedMap["f2.txt"])
		org.junit.jupiter.api.Assertions.assertTrue(compactedFile.length() < packFile.length())
	}

	@Test
	fun testCorruptionHandling(@TempDir tempDir: File) {
		val packFile = File(tempDir, "corrupt.pack")
		val file1 = BinaryContent("Valid content".toByteArray(), "valid.txt", "txt")
		BinaryPack.create(listOf(file1), FileSink(packFile, append = false))

		val originalBytes = packFile.readBytes()

		// 1. Corrupt magic number: should cleanly fail to read block, no crash/OOM
		val badMagicBytes = originalBytes.clone()
		badMagicBytes[0] = 0x00
		badMagicBytes[1] = 0x00
		val packBadMagic = BinaryPack(r3.source.BinarySource(badMagicBytes))
		assertEquals(0, packBadMagic.count())

		// 2. Corrupt CRC32: flip a byte in the index payload
		val badCrcBytes = originalBytes.clone()
		badCrcBytes[26] = (badCrcBytes[26].toInt() xor 0xFF).toByte()
		val packBadCrc = BinaryPack(r3.source.BinarySource(badCrcBytes))
		assertEquals(0, packBadCrc.count())

		// 3. Truncated stream
		val truncatedBytes = originalBytes.copyOf(15) // truncated inside header
		val packTruncated = BinaryPack(r3.source.BinarySource(truncatedBytes))
		assertEquals(0, packTruncated.count())
	}

	@Test
	fun testLegacyToNewConversion(@TempDir tempDir: File) {
		val legacyFile = File(tempDir, "legacy.pack")

		// Write a file in the old format: [StringWritable name][StringWritable type][Long length][Long created][Payload]
		legacyFile.outputStream().use { ostream ->
			java.io.DataOutputStream(ostream).use { dos ->
				// File 1
				r3.source.StringWritable("legacy1.txt").write(dos)
				r3.source.StringWritable("txt").write(dos)
				dos.writeLong(14L) // length of "Hello Legacy 1"
				dos.writeLong(123456789L)
				dos.write("Hello Legacy 1".toByteArray())

				// File 2
				val jsonBytes = "{\"key\": \"val\"}".toByteArray()
				r3.source.StringWritable("folder/legacy2.json").write(dos)
				r3.source.StringWritable("json").write(dos)
				dos.writeLong(jsonBytes.size.toLong())
				dos.writeLong(987654321L)
				dos.write(jsonBytes)
			}
		}

		org.junit.jupiter.api.Assertions.assertFalse(BinaryPackConverter.isNewFormat(legacyFile))

		// Convert to new format
		val converted = BinaryPackConverter.convertFile(legacyFile, keepBackup = true)
		org.junit.jupiter.api.Assertions.assertTrue(converted)
		org.junit.jupiter.api.Assertions.assertTrue(BinaryPackConverter.isNewFormat(legacyFile))

		// Verify backup exists
		val backupFile = File(tempDir, "legacy.pack.old")
		org.junit.jupiter.api.Assertions.assertTrue(backupFile.exists())

		// Read converted pack with new BinaryPack
		val newPack = BinaryPack(FileSource(legacyFile))
		val packMap = PackMap(newPack)

		assertEquals(2, newPack.count())
		assertEquals("Hello Legacy 1", packMap["legacy1.txt"]?.readText())
		assertEquals("{\"key\": \"val\"}", packMap["folder/legacy2.json"]?.readText())

		// Calling convert again on already converted file should return false (skipped)
		val secondRun = BinaryPackConverter.convertFile(legacyFile)
		org.junit.jupiter.api.Assertions.assertFalse(secondRun)
	}
}
