package r3.math

import r3.encryption.createCipherKey
import r3.pke.Password256
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.withLock

/**
 * A multi-threaded, lookahead prefetching implementation of [EncryptedSequence].
 *
 * Precomputes keystream blocks in advance across worker threads using a circular ring buffer,
 * pipelining AES keystream generation concurrently with consumer I/O.
 */
class PrefetchedEncryptedSequence(
	pass: Password256,
	val prefetchBlocks: Int = DEFAULT_PREFETCH_BLOCKS,
	val numThreads: Int = DEFAULT_NUM_THREADS,
	m: LMatrix? = null
) : EncryptedSequence(pass, m) {

	init {
		require(prefetchBlocks >= 1) { "prefetchBlocks must be at least 1, but was $prefetchBlocks" }
		require(numThreads >= 1) { "numThreads must be at least 1, but was $numThreads" }
	}

	private val cipherKey = createCipherKey(pass)
	private val nonceHigh = ByteBuffer.wrap(cipherKey.iv).order(ByteOrder.LITTLE_ENDIAN).getLong(0)
	private val nonceLow = ByteBuffer.wrap(cipherKey.iv).order(ByteOrder.LITTLE_ENDIAN).getLong(8)

	private class Slot {
		val buffer = ByteArray(BLOCKSIZE)
		@Volatile var blockNum = -1L
		@Volatile var state = STATE_EMPTY
	}

	private val slots = Array(prefetchBlocks) { Slot() }

	private val lock = ReentrantLock()
	private val hasSpace = lock.newCondition()
	private val hasData = lock.newCondition()

	private var head = 0L
	private var tail = 0L
	private val closed = AtomicBoolean(false)

	private var activeBlock: ByteArray? = null
	private var activeBlockNum = -1L

	private class WorkerState(key: ByteArray) {
		private val aesKey = SecretKeySpec(key, "AES")
		private val cipher: Cipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
			init(Cipher.ENCRYPT_MODE, aesKey)
		}
		private val blockBuffer = ByteArray(BLOCKSIZE)
		private val blockBb: ByteBuffer = ByteBuffer.wrap(blockBuffer).order(ByteOrder.LITTLE_ENDIAN)

		fun computeBlock(n: Long, nonceHigh: Long, nonceLow: Long, out: ByteArray) {
			var counter = n * (BLOCKSIZE / 16)
			for (i in 0 until (BLOCKSIZE / 16)) {
				val offset = i * 16
				blockBb.putLong(offset, nonceHigh xor counter)
				blockBb.putLong(offset + 8, nonceLow xor (counter ushr 32))
				counter++
			}
			cipher.update(blockBuffer, 0, BLOCKSIZE, out, 0)
		}
	}

	private fun producerLoop() {
		val worker = WorkerState(cipherKey.key)
		while (!closed.get()) {
			var targetBlock: Long
			var slotIndex: Int

			lock.withLock {
				while (!closed.get() && tail >= head + prefetchBlocks) {
					try {
						hasSpace.await()
					} catch (e: InterruptedException) {
						return
					}
				}
				if (closed.get()) return
				targetBlock = tail++
				slotIndex = (targetBlock % prefetchBlocks).toInt()
				slots[slotIndex].state = STATE_RESERVED
				slots[slotIndex].blockNum = targetBlock
			}

			// Generate keystream block directly into slot buffer outside lock
			worker.computeBlock(targetBlock, nonceHigh, nonceLow, slots[slotIndex].buffer)

			lock.withLock {
				if (!closed.get() && slots[slotIndex].blockNum == targetBlock && slots[slotIndex].state == STATE_RESERVED) {
					slots[slotIndex].state = STATE_READY
					hasData.signalAll()
				}
			}
		}
	}

	private val workers = Array(numThreads) { threadIdx ->
		Thread({
			producerLoop()
		}, "PrefetchedEncryptedSeq-Worker-$threadIdx").apply {
			isDaemon = true
			start()
		}
	}

	private fun releaseActiveBlock() {
		val relNum = activeBlockNum
		activeBlockNum = -1L
		activeBlock = null
		if (relNum == -1L) return

		val slotIndex = (relNum % prefetchBlocks).toInt()
		lock.withLock {
			if (slots[slotIndex].blockNum == relNum) {
				slots[slotIndex].state = STATE_EMPTY
				slots[slotIndex].blockNum = -1L
			}
			if (head <= relNum) {
				head = relNum + 1
			}
			hasSpace.signalAll()
		}
	}

	override fun getBlock(n: Long): ByteArray {
		if (n == activeBlockNum && activeBlock != null) {
			return activeBlock!!
		}

		if (activeBlockNum != -1L) {
			releaseActiveBlock()
		}

		val slotIndex = (n % prefetchBlocks).toInt()
		lock.withLock {
			if (n < head || n >= head + prefetchBlocks) {
				// Seek / jump detected: reset prefetch window starting at n
				for (slot in slots) {
					slot.state = STATE_EMPTY
					slot.blockNum = -1L
				}
				head = n
				tail = n
				hasSpace.signalAll()
			}

			while (!closed.get() && (slots[slotIndex].blockNum != n || slots[slotIndex].state != STATE_READY)) {
				try {
					hasData.await()
				} catch (e: InterruptedException) {
					throw RuntimeException("Interrupted waiting for encrypted block $n", e)
				}
			}
			if (closed.get()) {
				throw IllegalStateException("PrefetchedEncryptedSequence is closed")
			}

			activeBlockNum = n
			activeBlock = slots[slotIndex].buffer
			return activeBlock!!
		}
	}

	override fun get(pos: Long): Byte {
		val num = pos / BLOCKSIZE
		val block = getBlock(num)
		return block[(pos % BLOCKSIZE).toInt()]
	}

	override fun xor(b: ByteArray, off: Int, len: Int, streamPos: Long) {
		var curPos = streamPos
		var curOff = off
		var remaining = len
		while (remaining > 0) {
			val bNum = curPos / BLOCKSIZE
			val bOffset = (curPos % BLOCKSIZE).toInt()
			val block = getBlock(bNum)
			val toProcess = minOf(remaining, BLOCKSIZE - bOffset)
			for (i in 0 until toProcess) {
				b[curOff + i] = (b[curOff + i].toInt() xor block[bOffset + i].toInt()).toByte()
			}
			curPos += toProcess
			curOff += toProcess
			remaining -= toProcess
		}
	}

	override fun close() {
		if (closed.compareAndSet(false, true)) {
			lock.withLock {
				hasSpace.signalAll()
				hasData.signalAll()
			}
			for (worker in workers) {
				worker.interrupt()
			}
		}
	}

	companion object {
		const val DEFAULT_PREFETCH_BLOCKS = 32
		const val DEFAULT_NUM_THREADS = 2

		private const val STATE_EMPTY = 0
		private const val STATE_RESERVED = 1
		private const val STATE_READY = 2
	}
}
