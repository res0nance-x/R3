package r3.math

import r3.encryption.createCipherKey
import r3.pke.Password256
import r3.util.srnd
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger

class EncryptedSequenceHeader(private val hardPass: Password256, val m: LMatrix) {
	fun write(userPass: ByteArray, dos: DataOutputStream) {
		val encrypt = createCipherKey(userPass).createEncrypt()
		val baos = ByteArrayOutputStream(128)
		val out = DataOutputStream(baos)
		// padding with random bytes operates the same way as random iv in case of reused passwords
		out.writeLong(srnd.nextLong())
		out.writeLong(srnd.nextLong())
		out.writeLong(m.prime)
		hardPass.write(out)
		m.write(out)
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
		return EncryptedSequence(this.hardPass, this.m)
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
			val prime = inn.readLong()
			val hardPass = Password256.read(inn)
			return EncryptedSequenceHeader(
				hardPass,
				LMatrix.read(inn, prime)
			)
		}
	}
}

class EncryptedSequence(val pass: Password256, val m: LMatrix) {
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
			blockNum = num
			block = getBlock(blockNum)
		}
		return block[(pos % BLOCKSIZE).toInt()]
	}

	companion object {
		private val BLOCKSIZE = 4096
		fun createSequence(pass: Password256): EncryptedSequence {
			val factors = arrayOf(
				BigInteger.valueOf(2),
				BigInteger.valueOf(134215703),
				BigInteger.valueOf(72055420532431057L)
			)
			val m = Matrix.getMaximal(pass.arr, 3, BigInteger.valueOf(268431407L), factors)
			val arr = m.arr.map { it.toLong() }.toLongArray()
			val lm = LMatrix(arr, 3, 3, 268431407L)
			return EncryptedSequence(pass, lm)
		}
	}
}