package r3.encryption

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import r3.math.EncryptedSequence
import r3.math.PrefetchedEncryptedSequence
import r3.pke.Password256
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random

class PrefetchedEncryptedSequenceTest {

	@Test
	fun testBlockEquivalence() {
		val pass = Password256.createPassword()
		val standard = EncryptedSequence(pass)
		PrefetchedEncryptedSequence(pass, prefetchBlocks = 16, numThreads = 2).use { prefetched ->
			val blockIndices = listOf(0L, 1L, 2L, 5L, 15L, 16L, 17L, 31L, 32L, 100L)
			for (bIdx in blockIndices) {
				val stdBlock = standard.getBlock(bIdx)
				val prefBlock = prefetched.getBlock(bIdx)
				assertArrayEquals(stdBlock, prefBlock, "Block $bIdx keystream must match exactly")
			}
		}
	}

	@Test
	fun testByteByByteEquivalence() {
		val pass = Password256.createPassword()
		val standard = EncryptedSequence(pass)
		PrefetchedEncryptedSequence(pass, prefetchBlocks = 8, numThreads = 2).use { prefetched ->
			for (pos in 0L until 20_000L) {
				assertEquals(standard.get(pos), prefetched.get(pos), "Byte at position $pos must match")
			}
		}
	}

	@Test
	fun testXorEquivalenceAcrossChunkSizes() {
		val pass = Password256.createPassword()
		val totalBytes = 2 * 1024 * 1024 // 2 MB
		val random = Random(42)
		val originalData = ByteArray(totalBytes).also { random.nextBytes(it) }

		val chunkSizes = listOf(64, 512, 4096, 4099, 8192, 16384, 65536)

		for (chunkSize in chunkSizes) {
			val dataStd = originalData.copyOf()
			val dataPref = originalData.copyOf()

			val standard = EncryptedSequence(pass)
			var pos = 0L
			var offset = 0
			while (offset < totalBytes) {
				val len = minOf(chunkSize, totalBytes - offset)
				standard.xor(dataStd, offset, len, pos)
				offset += len
				pos += len
			}

			PrefetchedEncryptedSequence(pass, prefetchBlocks = 32, numThreads = 2).use { prefetched ->
				pos = 0L
				offset = 0
				while (offset < totalBytes) {
					val len = minOf(chunkSize, totalBytes - offset)
					prefetched.xor(dataPref, offset, len, pos)
					offset += len
					pos += len
				}
			}

			assertArrayEquals(dataStd, dataPref, "Ciphertext with chunkSize=$chunkSize must be identical")
		}
	}

	@Test
	fun testDecryptionRoundTrip() {
		val pass = Password256.createPassword()
		val data = ByteArray(500_000) { (it % 256).toByte() }
		val cipherText = data.copyOf()

		// Encrypt with Prefetched
		PrefetchedEncryptedSequence(pass).use { pref ->
			pref.xor(cipherText, 0, cipherText.size, 0L)
		}

		// Decrypt with Standard EncryptedSequence
		val decrypted = cipherText.copyOf()
		val standard = EncryptedSequence(pass)
		standard.xor(decrypted, 0, decrypted.size, 0L)

		assertArrayEquals(data, decrypted, "Decryption roundtrip must restore original plaintext exactly")
	}

	@Test
	fun testSeekingAndRandomAccess() {
		val pass = Password256.createPassword()
		val standard = EncryptedSequence(pass)
		PrefetchedEncryptedSequence(pass, prefetchBlocks = 16, numThreads = 2).use { prefetched ->
			val jumpBlocks = listOf(0L, 50L, 2L, 80L, 81L, 10L, 500L, 0L, 3L)
			for (bIdx in jumpBlocks) {
				val expected = standard.getBlock(bIdx)
				val actual = prefetched.getBlock(bIdx)
				assertArrayEquals(expected, actual, "Keystream block $bIdx after jump must match")
			}
		}
	}

	@Test
	fun testThreadConfigurations() {
		val pass = Password256.createPassword()
		val testLen = 256 * 1024
		val baseData = ByteArray(testLen) { (it and 0xFF).toByte() }

		val standardCipher = baseData.copyOf()
		EncryptedSequence(pass).xor(standardCipher, 0, testLen, 0L)

		for (threads in listOf(1, 2, 4)) {
			for (blocks in listOf(2, 8, 32)) {
				val prefCipher = baseData.copyOf()
				PrefetchedEncryptedSequence(pass, prefetchBlocks = blocks, numThreads = threads).use { pref ->
					pref.xor(prefCipher, 0, testLen, 0L)
				}
				assertArrayEquals(standardCipher, prefCipher, "Threads=$threads, Blocks=$blocks must match standard output")
			}
		}
	}

	@Test
	fun testEncryptedContinuousOutputStreamIntegration() {
		val pass = Password256.createPassword()
		val data = ByteArray(200_000) { (it * 7).toByte() }

		val baos = ByteArrayOutputStream()
		PrefetchedEncryptedSequence(pass).use { prefSeq ->
			EncryptedContinuousOutputStream(prefSeq, baos).use { out ->
				out.write(data)
			}
		}
		val encryptedBytes = baos.toByteArray()

		// Decrypt using standard EncryptedContinuousInputStream
		val decryptedBaos = ByteArrayOutputStream()
		val stdSeq = EncryptedSequence(pass)
		EncryptedContinuousInputStream(stdSeq, ByteArrayInputStream(encryptedBytes)).use { input ->
			input.copyTo(decryptedBaos)
		}

		assertArrayEquals(data, decryptedBaos.toByteArray(), "Encrypted stream via PrefetchedSequence must decrypt properly")
	}

	@Test
	fun benchmarkPrefetchedVsStandard() {
		val pass = Password256.createPassword()
		val totalMB = 50
		val totalBytes = totalMB * 1024 * 1024
		val chunk8k = ByteArray(8192) { 0x5a }
		val numChunks = totalBytes / chunk8k.size

		class NullOutputStream : java.io.OutputStream() {
			var written = 0L
			override fun write(b: Int) { written++ }
			override fun write(b: ByteArray, off: Int, len: Int) { written += len }
		}

		println("\n=======================================================")
		println("BENCHMARK: Standard EncryptedSequence vs Prefetched ($totalMB MB)")
		println("=======================================================")

		// 1. Standard EncryptedSequence
		val nullOut1 = NullOutputStream()
		val stdSeq = EncryptedSequence(pass)
		val stdOut = EncryptedContinuousOutputStream(stdSeq, nullOut1)
		val stdNanos = kotlin.system.measureNanoTime {
			for (i in 0 until numChunks) {
				stdOut.write(chunk8k, 0, chunk8k.size)
			}
			stdOut.close()
		}
		val stdMBps = totalMB / (stdNanos / 1_000_000_000.0)
		println("[1] Standard EncryptedSequence: ${"%.2f".format(stdNanos / 1_000_000.0)} ms -> ${"%.2f".format(stdMBps)} MB/s")

		// 2. PrefetchedEncryptedSequence (1 worker thread)
		val nullOut2 = NullOutputStream()
		val prefSeq1 = PrefetchedEncryptedSequence(pass, prefetchBlocks = 32, numThreads = 1)
		val prefOut1 = EncryptedContinuousOutputStream(prefSeq1, nullOut2)
		val pref1Nanos = kotlin.system.measureNanoTime {
			for (i in 0 until numChunks) {
				prefOut1.write(chunk8k, 0, chunk8k.size)
			}
			prefOut1.close()
			prefSeq1.close()
		}
		val pref1MBps = totalMB / (pref1Nanos / 1_000_000_000.0)
		println("[2] Prefetched (1 worker thread, 32 blocks): ${"%.2f".format(pref1Nanos / 1_000_000.0)} ms -> ${"%.2f".format(pref1MBps)} MB/s")

		// 3. PrefetchedEncryptedSequence (2 worker threads)
		val nullOut3 = NullOutputStream()
		val prefSeq2 = PrefetchedEncryptedSequence(pass, prefetchBlocks = 32, numThreads = 2)
		val prefOut2 = EncryptedContinuousOutputStream(prefSeq2, nullOut3)
		val pref2Nanos = kotlin.system.measureNanoTime {
			for (i in 0 until numChunks) {
				prefOut2.write(chunk8k, 0, chunk8k.size)
			}
			prefOut2.close()
			prefSeq2.close()
		}
		val pref2MBps = totalMB / (pref2Nanos / 1_000_000_000.0)
		println("[3] Prefetched (2 worker threads, 32 blocks): ${"%.2f".format(pref2Nanos / 1_000_000.0)} ms -> ${"%.2f".format(pref2MBps)} MB/s")

		// 4. PrefetchedEncryptedSequence (4 worker threads)
		val nullOut4 = NullOutputStream()
		val prefSeq4 = PrefetchedEncryptedSequence(pass, prefetchBlocks = 64, numThreads = 4)
		val prefOut4 = EncryptedContinuousOutputStream(prefSeq4, nullOut4)
		val pref4Nanos = kotlin.system.measureNanoTime {
			for (i in 0 until numChunks) {
				prefOut4.write(chunk8k, 0, chunk8k.size)
			}
			prefOut4.close()
			prefSeq4.close()
		}
		val pref4MBps = totalMB / (pref4Nanos / 1_000_000_000.0)
		println("[4] Prefetched (4 worker threads, 64 blocks): ${"%.2f".format(pref4Nanos / 1_000_000.0)} ms -> ${"%.2f".format(pref4MBps)} MB/s")

		// 5. Real File-to-File Disk Encryption Benchmark
		val tempInFile = java.io.File.createTempFile("bench_in_", ".bin")
		val tempOutStd = java.io.File.createTempFile("bench_out_std_", ".bin")
		val tempOutPref = java.io.File.createTempFile("bench_out_pref_", ".bin")
		try {
			java.io.FileOutputStream(tempInFile).buffered().use { fos ->
				val buf = ByteArray(65536) { 0x42 }
				repeat(totalBytes / buf.size) { fos.write(buf) }
			}

			// File encrypt with Standard
			val stdFileNanos = kotlin.system.measureNanoTime {
				val seq = EncryptedSequence(pass)
				java.io.BufferedOutputStream(java.io.FileOutputStream(tempOutStd)).use { fos ->
					EncryptedContinuousOutputStream(seq, fos).use { encOut ->
						java.io.BufferedInputStream(java.io.FileInputStream(tempInFile)).use { fin ->
							fin.copyTo(encOut)
						}
					}
				}
			}
			val stdFileMBps = totalMB / (stdFileNanos / 1_000_000_000.0)
			println("[5] Real File I/O Standard EncryptedSequence: ${"%.2f".format(stdFileNanos / 1_000_000.0)} ms -> ${"%.2f".format(stdFileMBps)} MB/s")

			// File encrypt with Prefetched (2 worker threads)
			val prefFileNanos = kotlin.system.measureNanoTime {
				val seq = PrefetchedEncryptedSequence(pass, prefetchBlocks = 64, numThreads = 2)
				java.io.BufferedOutputStream(java.io.FileOutputStream(tempOutPref)).use { fos ->
					EncryptedContinuousOutputStream(seq, fos).use { encOut ->
						java.io.BufferedInputStream(java.io.FileInputStream(tempInFile)).use { fin ->
							fin.copyTo(encOut)
						}
					}
				}
			}
			val prefFileMBps = totalMB / (prefFileNanos / 1_000_000_000.0)
			println("[6] Real File I/O Prefetched (2 threads, 64 blocks): ${"%.2f".format(prefFileNanos / 1_000_000.0)} ms -> ${"%.2f".format(prefFileMBps)} MB/s")

			// Verify file outputs are identical
			assertTrue(tempOutStd.readBytes().contentEquals(tempOutPref.readBytes()), "Disk file contents must be identical!")
		} finally {
			tempInFile.delete()
			tempOutStd.delete()
			tempOutPref.delete()
		}

		println("=======================================================\n")
	}
}
