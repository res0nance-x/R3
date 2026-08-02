package r3.math

import r3.io.Writable
import r3.util.srnd
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.util.*
import kotlin.experimental.and
import kotlin.experimental.xor

class BitMatrix(val width: Int, val height: Int, val bitSet: ByteArray = ByteArray(width * height)) : Writable {
	operator fun get(i: Int, j: Int): Byte {
		return bitSet[j * width + i]
	}

	operator fun set(i: Int, j: Int, value: Byte) {
		bitSet[j * width + i] = value
	}

	val isDiagonal: Boolean
		get() {
			for (i in 0 until width) {
				for (j in 0 until height) {
					if (i != j && get(i, j) > 0) {
						return false
					}
				}
			}
			return true
		}
	val isIdentity: Boolean
		get() {
			for (i in 0 until width) {
				for (j in 0 until height) {
					if (i == j) {
						if (get(i, j) == 0.toByte()) {
							return false
						}
					} else if (get(i, j) == 1.toByte()) {
						return false
					}
				}
			}
			return true
		}

	operator fun plus(b: BitMatrix): BitMatrix {
		val rm = BitMatrix(width, height)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				rm[i, j] = (this[i, j] xor b[i, j])
			}
		}
		return rm
	}

	operator fun minus(b: BitMatrix): BitMatrix {
		val rm = BitMatrix(width, height)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				rm[i, j] = (this[i, j] xor b[i, j])
			}
		}
		return rm
	}

	operator fun minusAssign(b: BitMatrix) {
		for (i in 0 until width) {
			for (j in 0 until height) {
				this[i, j] = (this[i, j] xor b[i, j])
			}
		}
	}

	operator fun plusAssign(b: BitMatrix) {
		for (i in 0 until width) {
			for (j in 0 until height) {
				this[i, j] = (this[i, j] xor b[i, j])
			}
		}
	}

	operator fun times(b: BitMatrix): BitMatrix {
		val rm = BitMatrix(b.width, height)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				var value = 0.toByte()
				for (k in 0 until b.height) {
					value = value xor (this[k, j] and b[i, k])
				}
				rm[i, j] = value
			}
		}
		return rm
	}

	operator fun div(b: BitMatrix): BitMatrix {
		return this.times(b.inverse())
	}

	fun mpow(y: BigInteger): BitMatrix {
		val len = y.bitLength()
		var v = identity(height)
		var sq = identity(height)
		for (i in 0 until len) {
			if (i == 0) {
				sq = this
			} else {
				sq = sq.times(sq)
			}
			if (y.testBit(i)) {
				v = sq.times(v)
			}
		}
		return v
	}

	fun inverse(): BitMatrix {
		return mpow(BigInteger.valueOf(2).pow(width) - BigInteger.valueOf(2))
	}

	fun random() {
		repeat(bitSet.size) {
			bitSet[it] = if (srnd.nextByte().toInt() and 0x1 == 0) 1 else 0
		}
	}

	override fun toString(): String {
		val buf = StringBuilder()
		for (j in 0 until height) {
			for (i in 0 until width) {
				buf.append(get(i, j))
			}
			buf.append('\n')
		}
		return buf.toString()
	}

	override fun write(dos: DataOutputStream) {
		val bs = BitSet(width * height)
		for (j in 0 until height) {
			for (i in 0 until width) {
				bs.set(j * width + i, get(i, j) == 1.toByte())
			}
		}
		val ba = bs.toByteArray()
		dos.writeShort(width)
		dos.writeShort(height)
		dos.writeInt(ba.size)
		dos.write(ba)
	}

	override fun equals(other: Any?): Boolean {
		if (other !is BitMatrix) return false
		return bitSet.contentEquals(other.bitSet)
	}

	companion object {
		fun identity(length: Int): BitMatrix {
			val m = BitMatrix(length, length)
			for (i in 0 until length) {
				m[i, i] = 1
			}
			return m
		}

		fun read(dis: DataInputStream): BitMatrix {
			val width = dis.readUnsignedShort()
			val height = dis.readUnsignedShort()
			val arrLength = dis.readInt()
			val ba = ByteArray(arrLength)
			dis.read(ba)
			val bs = BitSet.valueOf(ba)
			val m = BitMatrix(width, height)
			var count = 0
			for (j in 0 until height) {
				for (i in 0 until width) {
					m[i, j] = if (bs.get(count)) 1 else 0
					++count
				}
			}
			return m
		}

		fun getMaximal(dim: Int, factors: Array<BigInteger>? = null, maxRun: Int = 10000): BitMatrix {
			val perm = factors?.let { permutations(it) }
			val m = BitMatrix(dim, dim)
			var count = 0
			var found = false
			val ipow = BigInteger.valueOf(2).pow(dim).subtract(BigInteger.ONE)
			while (!found && count < maxRun) {
				m.random()
				val o = m.mpow(ipow)
				if (o.isIdentity) {
					found = true
				}
				perm?.also {
					for (i in perm.indices) {
						if (m.mpow(perm[i]).isIdentity) {
							found = false
							break
						}
					}
				}
				++count
			}
			if (count == maxRun) {
				throw Exception("No optimal matrix found within given iterations")
			}
			return m
		}
	}
}
