package r3.util

import r3.hash.hash512
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom

val srnd = DefaultRandomSequence()

interface RandomSequence {
	fun nextByte(): Byte
	fun nextInt(): Int
	fun nextLong(): Long
	fun nextDouble(): Double
	fun nextBytes(arr: ByteArray)
	fun getByteArray(n: Int): ByteArray
	fun getString(n: Int): String
}

class DefaultRandomSequence : RandomSequence {
	private val rnd = SecureRandom()
	private val charSet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
	override fun nextByte(): Byte {
		return rnd.nextInt().toByte()
	}

	override fun nextInt(): Int {
		return rnd.nextInt()
	}

	override fun nextLong(): Long {
		return rnd.nextLong()
	}

	override fun nextDouble(): Double {
		return rnd.nextDouble()
	}

	override fun nextBytes(arr: ByteArray) {
		rnd.nextBytes(arr)
	}

	override fun getByteArray(n: Int): ByteArray {
		val arr = ByteArray(n)
		rnd.nextBytes(arr)
		return arr
	}

	override fun getString(n: Int): String {
		val sb = StringBuilder()
		repeat(n) {
			sb.append(charSet[rnd.nextInt(charSet.length)])
		}
		return sb.toString()
	}
}

class LGMRandomSequence(iv: ByteArray) : RandomSequence {
	private val charSet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
	private val seed = iv.hash512()
	private val p = 268431407L
	private val dis = DataInputStream(ByteArrayInputStream(seed))
	private var v0 = (dis.readInt().toLong() and 0x7FFFFFFF) % p
	private var v1 = (dis.readInt().toLong() and 0x7FFFFFFF) % p
	private var v2 = (dis.readInt().toLong() and 0x7FFFFFFF) % p
	private var hash = seed.hash512()
	private var hpos = 0
	private fun nextHash() {
		v0 = (197474633 * v0 + 214282938 * v1 + 198029145 * v2) % p
		v1 = (196383522 * v0 + 206074781 * v1 + 139311274 * v2) % p
		v2 = (90385508 * v0 + 235632615 * v1 + 199951358 * v2) % p

		hash = ByteArrayOutputStream().use { baos ->
			DataOutputStream(baos).use { dos ->
				dos.write(hash)
				dos.writeInt(v0.toInt())
				dos.writeInt(v1.toInt())
				dos.writeInt(v2.toInt())
				baos.toByteArray().hash512()
			}
		}
	}

	override fun nextByte(): Byte {
		if (hpos >= hash.size) {
			nextHash()
			hpos = 0
		}
		return hash[hpos++]
	}

	override fun nextInt(): Int {
		return (((nextByte().toInt() and 0xFF) shl 24) or
				((nextByte().toInt() and 0xFF) shl 16) or
				((nextByte().toInt() and 0xFF) shl 8) or
				((nextByte().toInt() and 0xFF))) and 0x7fffffff
	}

	override fun nextLong(): Long {
		return (((nextByte().toLong() and 0xFFL) shl 56) or
				((nextByte().toLong() and 0xFFL) shl 48) or
				((nextByte().toLong() and 0xFFL) shl 40) or
				((nextByte().toLong() and 0xFFL) shl 32) or
				((nextByte().toLong() and 0xFFL) shl 24) or
				((nextByte().toLong() and 0xFFL) shl 16) or
				((nextByte().toLong() and 0xFFL) shl 8) or
				((nextByte().toLong() and 0xFFL))) and 0x7fffffffffffffffL
	}

	override fun nextDouble(): Double {
		return nextLong().toDouble() / Long.MAX_VALUE.toDouble()
	}

	override fun nextBytes(arr: ByteArray) {
		repeat(arr.size) {
			arr[it] = nextByte()
		}
	}

	override fun getByteArray(n: Int): ByteArray {
		return ByteArray(n) { nextByte() }
	}

	override fun getString(n: Int): String {
		val sb = StringBuilder()
		repeat(n) {
			sb.append(charSet[nextInt() % n])
		}
		return sb.toString()
	}
}