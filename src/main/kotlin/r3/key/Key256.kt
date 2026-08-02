package r3.key

import r3.hash.fromUBase64
import r3.hash.hash256
import r3.hash.toUBase64
import r3.io.Writable
import r3.source.Source
import r3.util.srnd
import java.io.DataInputStream
import java.io.DataOutputStream

open class Key256(val arr: ByteArray) : Comparable<Key256>, Writable {
	constructor(str: String) : this(str.fromUBase64())

	init {
		if (arr.size != 32) {
			throw RuntimeException("Expected arr of length 32 for Key256 constructor")
		}
	}

	override fun compareTo(other: Key256): Int {
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
		if (other !is Key256) {
			return false
		}
		return arr.contentEquals(other.arr)
	}

	override fun hashCode(): Int {
		return arr.contentHashCode()
	}

	infix fun xor(other: Key256): Key256 {
		return Key256(ByteArray(32) { (this.arr[it].toInt() xor other.arr[it].toInt()).toByte() })
	}

	companion object {
		fun read(dis: DataInputStream): Key256 {
			val key = Key256(ByteArray(32))
			dis.readFully(key.arr)
			return key
		}

		fun randomKey(): Key256 {
			val ba = ByteArray(32)
			srnd.nextBytes(ba)
			return Key256(ba)
		}

		val zeroKey = Key256(ByteArray(32))
	}
}

fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
	val minSize = if (a.size < b.size) a.size else b.size
	for (i in 0 until minSize) {
		val av = a[i]
		val bv = b[i]
		val cmp = av.compareTo(bv)
		if (cmp != 0) {
			return cmp
		}
	}
	return 0
}

fun Source.hash256(): ByteArray {
	return this.createInputStream().buffered().use {
		it.hash256()
	}
}