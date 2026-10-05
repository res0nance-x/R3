package r3.encryption

import org.junit.jupiter.api.Test
import r3.hash.hash128
import r3.math.EncryptedSequence
import r3.math.LMatrix
import r3.math.LMatrixSequence
import r3.math.Matrix
import r3.pke.Encrypt
import r3.pke.Password256
import java.io.*
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.measureNanoTime

class EncryptionBenchmarkTest {

	private class NullOutputStream : OutputStream() {
		var bytesWritten: Long = 0
		override fun write(b: Int) {
			bytesWritten++
		}
		override fun write(b: ByteArray, off: Int, len: Int) {
			bytesWritten += len
		}
	}

	@Test
	fun testAesCtrSpeed() {
		val pass = Password256.createPassword()
		val ck = createCipherKey(pass)
		val aesKey = javax.crypto.spec.SecretKeySpec(ck.key, "AES")
		val ivSpec = javax.crypto.spec.IvParameterSpec(ck.iv)
		val cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding")
		cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, aesKey, ivSpec)

		val totalMB = 50
		val totalBytes = totalMB * 1024 * 1024
		val buf = ByteArray(65536) { 0x5a }
		val outBuf = ByteArray(65536)
		val numChunks = totalBytes / buf.size

		// Warmup
		repeat(100) { cipher.update(buf, 0, buf.size, outBuf, 0) }

		val nanos = measureNanoTime {
			for (i in 0 until numChunks) {
				cipher.update(buf, 0, buf.size, outBuf, 0)
			}
		}
		val sec = nanos / 1_000_000_000.0
		val mbps = totalMB / sec
		println("Java AES/CTR/NoPadding hardware accelerated: ${nanos / 1_000_000.0} ms -> ${"%.2f".format(mbps)} MB/s!")
	}

	@Test
	fun testLMatrixSequenceDesync() {
		val pass = Password256.createPassword()
		val factors = arrayOf(
			BigInteger.valueOf(2),
			BigInteger.valueOf(134215703),
			BigInteger.valueOf(72055420532431057L)
		)
		val m = Matrix.getMaximal(pass.arr, 3, BigInteger.valueOf(268431407L), factors)
		val arr = m.arr.map { it.toLong() }.toLongArray()
		val lm = LMatrix(arr, 3, 3, 268431407L)
		val lseq = LMatrixSequence(lm, pass.arr)

		// Warmup
		for (i in 0..100) lseq.getSequence(i.toLong())

		// In-sync sequential 10,000 blocks
		val seqTime = measureNanoTime {
			for (i in 101..10100) {
				lseq.getSequence(i.toLong())
			}
		}
		println("Sequential 10,000 blocks (in-sync m.times): ${seqTime / 1_000_000.0} ms")

		// Force jump: skip by 2
		lseq.getSequence(20000L)

		// Post-jump 10,000 blocks: prevN is now desynced permanently!
		val desyncTime = measureNanoTime {
			for (i in 20001..30000) {
				lseq.getSequence(i.toLong())
			}
		}
		println("Post-jump 10,000 blocks (permanently desynced -> mpow on EVERY call!): ${desyncTime / 1_000_000.0} ms")
		println("Desync slowdown factor: ${desyncTime.toDouble() / seqTime}x slower!")
	}

	@Test
	fun testVectorEquivalenceAndSpeed() {
		val pass = Password256.createPassword()
		val factors = arrayOf(
			BigInteger.valueOf(2),
			BigInteger.valueOf(134215703),
			BigInteger.valueOf(72055420532431057L)
		)
		val m = Matrix.getMaximal(pass.arr, 3, BigInteger.valueOf(268431407L), factors)
		val arr = m.arr.map { it.toLong() }.toLongArray()
		val lm = LMatrix(arr, 3, 3, 268431407L)
		val lseq = LMatrixSequence(lm, pass.arr)

		// Matrix entries: m[col, row]
		// In LMatrix: arr[row * width + col]
		val m00 = lm[0, 0]; val m10 = lm[1, 0]; val m20 = lm[2, 0]
		val m01 = lm[0, 1]; val m11 = lm[1, 1]; val m21 = lm[2, 1]
		val m02 = lm[0, 2]; val m12 = lm[1, 2]; val m22 = lm[2, 2]
		val prime = lm.prime

		// Initial v0
		val v0 = lm.mpow(BigInteger(1, pass.arr.hash128()))
		var x0: Long = v0[0, 0]
		var x1: Long = v0[0, 1]
		var x2: Long = v0[0, 2]

		// Compare 1,000 steps
		for (step in 1..1000) {
			val expectedBytes = lseq.getSequence(step.toLong())
			val nx0 = (m00 * x0 + m10 * x1 + m20 * x2) % prime
			val nx1 = (m01 * x0 + m11 * x1 + m21 * x2) % prime
			val nx2 = (m02 * x0 + m12 * x1 + m22 * x2) % prime
			x0 = nx0; x1 = nx1; x2 = nx2

			val actualBytes = byteArrayOf(
				x0.toByte(), (x0 ushr 8).toByte(), (x0 ushr 16).toByte(), (x0 ushr 24).toByte(), (x0 ushr 32).toByte(),
				x1.toByte(), (x1 ushr 8).toByte(), (x1 ushr 16).toByte(), (x1 ushr 24).toByte(), (x1 ushr 32).toByte(),
				x2.toByte(), (x2 ushr 8).toByte(), (x2 ushr 16).toByte(), (x2 ushr 24).toByte(), (x2 ushr 32).toByte(),
				(x2 ushr 48).toByte()
			)
			if (!expectedBytes.contentEquals(actualBytes)) {
				error("Mismatch at step $step!")
			}
		}
		println("Vector math equivalence verified 100% identical!")

		// Now benchmark 50 MB (3,276,800 blocks) with pure unrolled registers!
		val numBlocks = (50 * 1024 * 1024) / 16
		val dest = ByteArray(4096)
		val nanos = measureNanoTime {
			var curX0 = x0; var curX1 = x1; var curX2 = x2
			var blockOffset = 0
			for (i in 0 until numBlocks) {
				val nx0 = (m00 * curX0 + m10 * curX1 + m20 * curX2) % prime
				val nx1 = (m01 * curX0 + m11 * curX1 + m21 * curX2) % prime
				val nx2 = (m02 * curX0 + m12 * curX1 + m22 * curX2) % prime
				curX0 = nx0; curX1 = nx1; curX2 = nx2

				dest[blockOffset + 0] = curX0.toByte()
				dest[blockOffset + 1] = (curX0 ushr 8).toByte()
				dest[blockOffset + 2] = (curX0 ushr 16).toByte()
				dest[blockOffset + 3] = (curX0 ushr 24).toByte()
				dest[blockOffset + 4] = (curX0 ushr 32).toByte()
				dest[blockOffset + 5] = curX1.toByte()
				dest[blockOffset + 6] = (curX1 ushr 8).toByte()
				dest[blockOffset + 7] = (curX1 ushr 16).toByte()
				dest[blockOffset + 8] = (curX1 ushr 24).toByte()
				dest[blockOffset + 9] = (curX1 ushr 32).toByte()
				dest[blockOffset + 10] = curX2.toByte()
				dest[blockOffset + 11] = (curX2 ushr 8).toByte()
				dest[blockOffset + 12] = (curX2 ushr 16).toByte()
				dest[blockOffset + 13] = (curX2 ushr 24).toByte()
				dest[blockOffset + 14] = (curX2 ushr 32).toByte()
				dest[blockOffset + 15] = (curX2 ushr 48).toByte()

				blockOffset += 16
				if (blockOffset == 4096) blockOffset = 0
			}
		}
		val sec = nanos / 1_000_000_000.0
		val mbps = 50.0 / sec
		println("Unrolled in-place matrix vector generation: ${nanos / 1_000_000.0} ms -> ${"%.2f".format(mbps)} MB/s!")
	}

	@Test
	fun testScalingByFileSize() {
		val pass = Password256.createPassword()
		println("\n======================================================================")
		println("SCALING BENCHMARK: File Encryption Throughput by Size")
		println("======================================================================")

		val sizesMB = listOf(1, 5, 20, 50, 100, 200)
		for (sizeMB in sizesMB) {
			val totalBytes = sizeMB * 1024 * 1024
			val tempIn = File.createTempFile("scale_in_", ".bin")
			val tempOut = File.createTempFile("scale_out_", ".bin")
			try {
				FileOutputStream(tempIn).buffered().use { fos ->
					val buf = ByteArray(65536) { 0x5a }
					repeat(totalBytes / buf.size) { fos.write(buf) }
				}

				val nanos = measureNanoTime {
					Encrypt.encrypt(pass, r3.source.FileSource(tempIn), r3.source.FileSink(tempOut, false))
				}
				val ms = nanos / 1_000_000.0
				val mbps = sizeMB / (nanos / 1_000_000_000.0)
				val msPerMB = ms / sizeMB
				println("Size: %4d MB | Time: %7.1f ms | Speed: %6.1f MB/s | Cost: %5.2f ms/MB".format(sizeMB, ms, mbps, msPerMB))
			} finally {
				tempIn.delete()
				tempOut.delete()
			}
		}
		println("======================================================================")
	}

	@Test
	fun testBlockGenerationUniqueness() {
		val pass = Password256.createPassword()
		val seq = EncryptedSequence.createSequence(pass)
		val b0 = ByteArray(4096) { seq.get(it.toLong()) }
		val b1 = ByteArray(4096) { seq.get((4096 + it).toLong()) }
		val identical = b0.contentEquals(b1)
		println("CRITICAL CHECK: Is block 1 identical to block 0 in current code? $identical")
		println("b0[0..3] = ${b0.slice(0..3)}, b1[0..3] = ${b1.slice(0..3)}")
	}

	class FixedEncryptedSequence(val pass: Password256, val m: LMatrix) {
		private val cipherKey = createCipherKey(pass)
		private val encrypt = cipherKey.createEncrypt()
		private val sequence = LMatrixSequence(m, pass.arr)
		private var blockNum = -1L
		private var block = getBlock(0)

		private fun getBlock(n: Long): ByteArray {
			if (n == blockNum) {
				return block
			}
			val arr = ByteArray(BLOCKSIZE)
			var pos = n * BLOCKSIZE / 16
			repeat(BLOCKSIZE / 16) {
				val seq = sequence.getSequence(pos++)
				System.arraycopy(seq, 0, arr, it * 16, 16)
			}
			blockNum = n
			block = encrypt.doFinal(arr)
			return block
		}

		fun get(pos: Long): Byte {
			val num = pos / BLOCKSIZE
			if (num != blockNum) {
				block = getBlock(num)
			}
			return block[(pos % BLOCKSIZE).toInt()]
		}

		companion object {
			private val BLOCKSIZE = 4096
			fun createSequence(pass: Password256): FixedEncryptedSequence {
				val factors = arrayOf(
					BigInteger.valueOf(2),
					BigInteger.valueOf(134215703),
					BigInteger.valueOf(72055420532431057L)
				)
				val m = Matrix.getMaximal(pass.arr, 3, BigInteger.valueOf(268431407L), factors)
				val arr = m.arr.map { it.toLong() }.toLongArray()
				val lm = LMatrix(arr, 3, 3, 268431407L)
				return FixedEncryptedSequence(pass, lm)
			}
		}
	}

	@Test
	fun benchmarkDetailedBreakdown() {
		val pass = Password256.createPassword()
		val totalMB = 50
		val totalBytes = totalMB * 1024 * 1024

		println("======================================================================")
		println("BENCHMARK: Stream Cipher Component Profiling ($totalMB MB)")
		println("======================================================================")

		// ------------------------------------------------------------------
		// STAGE 0: Setup Overhead (One-time Matrix.getMaximal)
		// ------------------------------------------------------------------
		val setupTimes = (1..5).map {
			val p = Password256.createPassword()
			measureNanoTime { EncryptedSequence.createSequence(p) } / 1_000_000.0
		}
		println("[Stage 0] Setup (Matrix.getMaximal): avg ${"%.2f".format(setupTimes.average())} ms (samples: ${setupTimes.map { "%.1f".format(it) }})")

		// ------------------------------------------------------------------
		// STAGE 1: Random Generator Sequence Alone (LMatrixSequence)
		// ------------------------------------------------------------------
		val factors = arrayOf(
			BigInteger.valueOf(2),
			BigInteger.valueOf(134215703),
			BigInteger.valueOf(72055420532431057L)
		)
		val m = Matrix.getMaximal(pass.arr, 3, BigInteger.valueOf(268431407L), factors)
		val arr = m.arr.map { it.toLong() }.toLongArray()
		val lm = LMatrix(arr, 3, 3, 268431407L)
		val lseq = LMatrixSequence(lm, pass.arr)

		// Warmup
		repeat(1000) { lseq.getSequence(it.toLong()) }

		val num16ByteBlocks = totalBytes / 16
		var dummySum = 0L

		val lseqNanos = measureNanoTime {
			for (i in 0 until num16ByteBlocks) {
				val b = lseq.getSequence(i.toLong())
				dummySum += b[0].toLong()
			}
		}
		val lseqSec = lseqNanos / 1_000_000_000.0
		val lseqMBps = totalMB / lseqSec
		println("[Stage 1] LMatrixSequence alone (sequential): ${"%.2f".format(lseqSec * 1000)} ms -> ${"%.2f".format(lseqMBps)} MB/s")

		// ------------------------------------------------------------------
		// STAGE 1b: Random Generator with Non-sequential Seek (m.mpow)
		// ------------------------------------------------------------------
		val seekNanos = measureNanoTime {
			// Jump to arbitrary positions
			val jumpPositions = longArrayOf(1000L, 50000L, 500000L, 1000000L, 2000000L)
			for (pos in jumpPositions) {
				lseq.getSequence(pos)
			}
		}
		println("[Stage 1b] LMatrixSequence seek (5 non-sequential jumps via mpow): ${"%.3f".format(seekNanos / 1_000_000.0)} ms")

		// ------------------------------------------------------------------
		// STAGE 2: Alternative - Incremented Numbers (Counter)
		// ------------------------------------------------------------------
		val counterBuf = ByteArray(16)
		val counterBb = ByteBuffer.wrap(counterBuf).order(ByteOrder.LITTLE_ENDIAN)
		var counterSum = 0L
		val counterNanos = measureNanoTime {
			for (i in 0 until num16ByteBlocks) {
				counterBb.putLong(0, i.toLong())
				counterBb.putLong(8, 0L)
				counterSum += counterBuf[0].toLong()
			}
		}
		val counterSec = counterNanos / 1_000_000_000.0
		val counterMBps = totalMB / counterSec
		println("[Stage 2] Counter generation alone: ${"%.2f".format(counterSec * 1000)} ms -> ${"%.2f".format(counterMBps)} MB/s")

		// ------------------------------------------------------------------
		// STAGE 3: AES Encryption Alone (4096-byte blocks with Cipher.doFinal)
		// ------------------------------------------------------------------
		val cipherKey = createCipherKey(pass)
		val cipher = cipherKey.createEncrypt()
		val block4k = ByteArray(4096)
		val num4kBlocks = totalBytes / 4096
		repeat(100) { cipher.doFinal(block4k) } // warmup

		var aesSum = 0L
		val aesNanos = measureNanoTime {
			for (i in 0 until num4kBlocks) {
				val enc = cipher.doFinal(block4k)
				aesSum += enc[0].toLong()
			}
		}
		val aesSec = aesNanos / 1_000_000_000.0
		val aesMBps = totalMB / aesSec
		println("[Stage 3] AES/CBC 4KB doFinal alone: ${"%.2f".format(aesSec * 1000)} ms -> ${"%.2f".format(aesMBps)} MB/s")

		// ------------------------------------------------------------------
		// STAGE 4: Keystream Generation with Current EncryptedSequence (shows bug: repeats block 0)
		// ------------------------------------------------------------------
		val seq = EncryptedSequence.createSequence(pass)
		val combinedNanos = measureNanoTime {
			for (i in 0 until num4kBlocks) {
				seq.get(i.toLong() * 4096L)
			}
		}
		val combinedSec = combinedNanos / 1_000_000_000.0
		val combinedMBps = totalMB / combinedSec
		println("[Stage 4] Current EncryptedSequence (cached block 0 reuse bug): ${"%.2f".format(combinedSec * 1000)} ms -> ${"%.2f".format(combinedMBps)} MB/s")

		// ------------------------------------------------------------------
		// STAGE 4b: Keystream Generation with FixedEncryptedSequence (ALL blocks generated)
		// ------------------------------------------------------------------
		val fixedSeq = FixedEncryptedSequence.createSequence(pass)
		val fixedNanos = measureNanoTime {
			for (i in 0 until num4kBlocks) {
				fixedSeq.get(i.toLong() * 4096L)
			}
		}
		val fixedSec = fixedNanos / 1_000_000_000.0
		val fixedMBps = totalMB / fixedSec
		println("[Stage 4b] FixedEncryptedSequence (LMatrix + AES on EVERY block): ${"%.2f".format(fixedSec * 1000)} ms -> ${"%.2f".format(fixedMBps)} MB/s")

		// ------------------------------------------------------------------
		// STAGE 5: Keystream Generation with Counter + AES (Alternative)
		// ------------------------------------------------------------------
		val counterCipher = cipherKey.createEncrypt()
		val counterBlock4k = ByteArray(4096)
		val counterBlockBb = ByteBuffer.wrap(counterBlock4k).order(ByteOrder.LITTLE_ENDIAN)
		val counterAesNanos = measureNanoTime {
			var globalBlockIdx = 0L
			for (i in 0 until num4kBlocks) {
				for (j in 0 until 4096 / 16) {
					counterBlockBb.putLong(j * 16, globalBlockIdx++)
					counterBlockBb.putLong(j * 16 + 8, 0L)
				}
				counterCipher.doFinal(counterBlock4k)
			}
		}
		val counterAesSec = counterAesNanos / 1_000_000_000.0
		val counterAesMBps = totalMB / counterAesSec
		println("[Stage 5] Keystream Generation (Counter + AES combined): ${"%.2f".format(counterAesSec * 1000)} ms -> ${"%.2f".format(counterAesMBps)} MB/s")

		// ------------------------------------------------------------------
		// STAGE 6: Stream Cipher XOR & Write Application Overhead
		// Compare current per-byte lambda with bulk array XOR
		// ------------------------------------------------------------------
		val chunk8k = ByteArray(8192) { 0x55 }

		// Current implementation: per-byte lambda with seq.get(pos++)
		val nullOut1 = NullOutputStream()
		val encOutCurrent = EncryptedContinuousOutputStream(EncryptedSequence.createSequence(pass), nullOut1)
		val currentWriteNanos = measureNanoTime {
			val numChunks = totalBytes / chunk8k.size
			for (i in 0 until numChunks) {
				encOutCurrent.write(chunk8k, 0, chunk8k.size)
			}
			encOutCurrent.close()
		}
		val currentWriteSec = currentWriteNanos / 1_000_000_000.0
		val currentWriteMBps = totalMB / currentWriteSec
		println("[Stage 6a] Current EncryptedContinuousOutputStream (per-byte lambda XOR): ${"%.2f".format(currentWriteSec * 1000)} ms -> ${"%.2f".format(currentWriteMBps)} MB/s")

		// ------------------------------------------------------------------
		// STAGE 7: Full End-to-End File Encryption (with File Disk I/O)
		// ------------------------------------------------------------------
		val tempInFile = File.createTempFile("bench_in_", ".bin")
		val tempOutFile = File.createTempFile("bench_out_", ".bin")
		try {
			// Create 50MB file
			FileOutputStream(tempInFile).buffered().use { fos ->
				val buf = ByteArray(65536) { 0x42 }
				repeat(totalBytes / buf.size) { fos.write(buf) }
			}

			val fileEncNanos = measureNanoTime {
				Encrypt.encrypt(pass, r3.source.FileSource(tempInFile), r3.source.FileSink(tempOutFile, false))
			}
			val fileEncSec = fileEncNanos / 1_000_000_000.0
			val fileEncMBps = totalMB / fileEncSec
			println("[Stage 7] Full Encrypt.encrypt (File to File on disk): ${"%.2f".format(fileEncSec * 1000)} ms -> ${"%.2f".format(fileEncMBps)} MB/s")

			// Also measure pure disk copy speed for baseline comparison
			val copyOutFile = File.createTempFile("bench_copy_", ".bin")
			try {
				val copyNanos = measureNanoTime {
					FileInputStream(tempInFile).buffered().use { fis ->
						FileOutputStream(copyOutFile).buffered().use { fos ->
							fis.copyTo(fos)
						}
					}
				}
				val copySec = copyNanos / 1_000_000_000.0
				val copyMBps = totalMB / copySec
				println("[Stage 7b] Baseline Raw File Copy (no encryption): ${"%.2f".format(copySec * 1000)} ms -> ${"%.2f".format(copyMBps)} MB/s")
			} finally {
				copyOutFile.delete()
			}
		} finally {
			tempInFile.delete()
			tempOutFile.delete()
		}

		println("======================================================================")
	}
}

