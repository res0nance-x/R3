package r3.key

import r3.hash.fromUBase64
import r3.hash.hash128
import r3.hash.toUBase64
import r3.io.Writable
import r3.source.Source
import r3.util.srnd
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream

class Key128(val arr: ByteArray) : Comparable<Key128>, Writable {
	init {
		if (arr.size != 16) {
			throw RuntimeException("Expected arr of length 16 for Key128 constructor")
		}
	}

	override fun compareTo(other: Key128): Int {
		return compareByteArrays(arr, other.arr)
	}

	override fun toString(): String {
		return arr.toUBase64()
	}

	override fun write(dos: DataOutputStream) {
		dos.write(arr)
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is Key128) {
			return false
		}
		return arr.contentEquals(other.arr)
	}

	override fun hashCode(): Int {
		return arr.contentHashCode()
	}

	fun toFileFriendlyName(): String {
		return arr.toUBase64()
	}

	companion object {
		fun read(dis: DataInputStream): Key128 {
			val key = Key128(ByteArray(16))
			dis.readFully(key.arr)
			return key
		}

		fun hashToKey128(stream: InputStream): Key128 {
			return Key128(stream.hash128())
		}

		fun hashToKey128(arr: ByteArray): Key128 {
			return Key128(arr.hash128())
		}

		fun fromFileFriendlyName(name: String): Key128 {
			val arr = name.fromUBase64()
			return Key128(arr)
		}

		fun randomKey(): Key128 {
			val ba = ByteArray(16)
			srnd.nextBytes(ba)
			return Key128(ba)
		}

		val zeroKey = Key128(ByteArray(16))
	}
}

infix fun Key128.xor(key: Key128): Key128 {
	val arr = ByteArray(16)
	repeat(16) {
		arr[it] = (arr[it].toInt() xor key.arr[it].toInt()).toByte()
	}
	return Key128(arr)
}

fun Source.hash128(): Key128 {
	return this.createInputStream().use {
		Key128(it.hash128())
	}
}