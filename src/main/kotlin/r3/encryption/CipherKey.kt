package r3.encryption

import r3.hash.hash256
import r3.hash.hash512
import r3.io.Writable
import r3.io.serialize
import r3.math.BitMatrix
import r3.math.Matrix
import r3.pke.Password256
import r3.util.srnd
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CipherKey(val iv: ByteArray, val key: ByteArray) : Writable {
	fun createEncrypt(): Cipher {
		val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
		val aesKey = SecretKeySpec(key, "AES")
		cipher.init(Cipher.ENCRYPT_MODE, aesKey, IvParameterSpec(iv))
		return cipher
	}

	fun createDecrypt(): Cipher {
		val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
		val aesKey = SecretKeySpec(key, "AES")
		cipher.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(iv))
		return cipher
	}

	override fun write(dos: DataOutputStream) {
		dos.write(iv)
		dos.write(key)
	}

	companion object {
		fun read(dis: DataInputStream): CipherKey {
			val iv = ByteArray(16)
			val key = ByteArray(32)
			dis.readFully(iv)
			dis.readFully(key)
			return CipherKey(iv, key)
		}
	}
}

fun createCipherKey(pass: String): CipherKey {
	return createCipherKey(pass.toByteArray())
}

fun createCipherKey(pass: Password256): CipherKey {
	val ba = pass.arr.hash512()
	val iv = ba.sliceArray(0..15)
	val key = ba.sliceArray(16..47)
	return CipherKey(iv, key)
}

fun createCipherKey(barr: ByteArray): CipherKey {
	val ba = barr.hash512()
	val iv = ba.sliceArray(0..15)
	val key = ba.sliceArray(16..47)
	return CipherKey(iv, key)
}

fun createCipherKey(pass: String, iv: ByteArray): CipherKey {
	return CipherKey(iv, pass.toByteArray().hash256())
}

fun createCipherKey(mxy: BigInteger): CipherKey {
	val hash = mxy.toByteArray().hash512()
	return createCipherKey(hash)
}

fun createCipherKey(mxy: Matrix): CipherKey {
	val hash = mxy.serialize().hash512()
	return createCipherKey(hash)
}

fun createCipherKey(mxy: BitMatrix): CipherKey {
	val hash = mxy.serialize().hash512()
	return createCipherKey(hash)
}

fun createRandomCipherKey(): CipherKey {
	val iv = srnd.getByteArray(16)
	val key = srnd.getByteArray(32)
	return CipherKey(iv, key)
}

fun padded(arr: ByteArray): ByteArray {
	val rem = arr.size % 16
	if (rem == 0) {
		return arr
	}
	val pad = ByteArray(16 - rem)
	srnd.nextBytes(pad)
	return arr + pad
}

fun zeroPadded(arr: ByteArray): ByteArray {
	val rem = arr.size % 16
	if (rem == 0) {
		return arr
	}
	val pad = ByteArray(16 - rem)
	return arr + pad
}