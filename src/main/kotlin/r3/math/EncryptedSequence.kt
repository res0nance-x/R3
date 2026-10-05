package r3.math

import r3.encryption.createCipherKey
import r3.pke.Password256
import r3.util.srnd
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class EncryptedSequenceHeader(private val hardPass: Password256, val m: LMatrix? = null) {
	fun write(userPass: ByteArray, dos: DataOutputStream) {
		val encrypt = createCipherKey(userPass).createEncrypt()
		val baos = ByteArrayOutputStream(128)
		val out = DataOutputStream(baos)
		// padding with random bytes operates the same way as random iv in case of reused passwords
		out.writeLong(srnd.nextLong())
		out.writeLong(srnd.nextLong())
		out.writeLong(0L)
		hardPass.write(out)
		while (baos.size() < 128) {
			baos.write(0)
		}
		val eArr = encrypt.doFinal(baos.toByteArray())
		dos.write(eArr)
	}

	fun toByteArray(userPass: ByteArray): ByteArray {
		return ByteArrayOutputStream().use { baos ->
			this.write(userPass, DataOutputStream(baos))
			baos.toByteArray()
		}
	}

	fun getEncryptedSequence(): EncryptedSequence {
		return EncryptedSequence(this.hardPass)
	}

	companion object {
		fun read(userPass: ByteArray, dis: DataInputStream): EncryptedSequenceHeader {
			val decrypt = createCipherKey(userPass).createDecrypt()
			val eArr = ByteArray(128)
			dis.readFully(eArr)
			val arr = decrypt.doFinal(eArr)
			val inn = DataInputStream(ByteArrayInputStream(arr))
			inn.readLong()
			inn.readLong()
			inn.readLong()
			val hardPass = Password256.read(inn)
			return EncryptedSequenceHeader(hardPass)
		}
	}
}

open class EncryptedSequence(val pass: Password256, val m: LMatrix? = null) : AutoCloseable {
	private val cipherKey = createCipherKey(pass)
	private val aesKey = SecretKeySpec(cipherKey.key, "AES")
	// Use AES/ECB/NoPadding to encrypt each 16-byte counter block independently (AES-CTR)
	private val cipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
		init(Cipher.ENCRYPT_MODE, aesKey)
	}

	private val nonceHigh = ByteBuffer.wrap(cipherKey.iv).order(ByteOrder.LITTLE_ENDIAN).getLong(0)
	private val nonceLow = ByteBuffer.wrap(cipherKey.iv).order(ByteOrder.LITTLE_ENDIAN).getLong(8)

	private val blockBuffer = ByteArray(BLOCKSIZE)
	private val blockBb = ByteBuffer.wrap(blockBuffer).order(ByteOrder.LITTLE_ENDIAN)
	private var currentBlock = ByteArray(BLOCKSIZE)
	private var blockNum = -1L

	open fun getBlock(n: Long): ByteArray {
		if (n == blockNum) {
			return currentBlock
		}
		var counter = n * (BLOCKSIZE / 16)
		for (i in 0 until (BLOCKSIZE / 16)) {
			val offset = i * 16
			blockBb.putLong(offset, nonceHigh xor counter)
			blockBb.putLong(offset + 8, nonceLow xor (counter ushr 32))
			counter++
		}
		blockNum = n
		cipher.update(blockBuffer, 0, BLOCKSIZE, currentBlock, 0)
		return currentBlock
	}

	open fun get(pos: Long): Byte {
		val num = pos / BLOCKSIZE
		if (num != blockNum) {
			currentBlock = getBlock(num)
		}
		return currentBlock[(pos % BLOCKSIZE).toInt()]
	}

	open fun xor(b: ByteArray, off: Int, len: Int, streamPos: Long) {
		var curPos = streamPos
		var curOff = off
		var remaining = len
		while (remaining > 0) {
			val bNum = curPos / BLOCKSIZE
			val bOffset = (curPos % BLOCKSIZE).toInt()
			if (bNum != blockNum) {
				currentBlock = getBlock(bNum)
			}
			val toProcess = minOf(remaining, BLOCKSIZE - bOffset)
			for (i in 0 until toProcess) {
				b[curOff + i] = (b[curOff + i].toInt() xor currentBlock[bOffset + i].toInt()).toByte()
			}
			curPos += toProcess
			curOff += toProcess
			remaining -= toProcess
		}
	}

	override fun close() {}

	companion object {
		const val BLOCKSIZE = 4096
		fun createSequence(pass: Password256): EncryptedSequence {
			return PrefetchedEncryptedSequence(pass)
		}
	}
}