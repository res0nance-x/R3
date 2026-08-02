package r3.math

import r3.io.Writable
import r3.util.srnd
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.util.*

class BitMatrix2(val width: Int, val height: Int, val bitSet: Array<Long> = Array(height) { 0L }) :
	Writable {
	operator fun get(i: Int, j: Int): Boolean {
		return (bitSet[j] ushr i) and 1L > 0
	}

	operator fun set(i: Int, j: Int, value: Boolean) {
		if (value) {
			bitSet[j] = (1L shl i) or bitSet[j]
		} else {
			bitSet[j] = ((1L shl i) xor -1L) and bitSet[j]
		}
	}

	val isDiagonal: Boolean
		get() {
			for (i in 0 until width) {
				for (j in 0 until height) {
					if (i != j && get(i, j)) {
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
						if (!get(i, j)) {
							return false
						}
					} else if (get(i, j)) {
						return false
					}
				}
			}
			return true
		}

	operator fun plus(b: BitMatrix2): BitMatrix2 {
		val rm = BitMatrix2(width, height)
		for (j in 0 until rm.height) {
			bitSet[j] = bitSet[j] xor b.bitSet[j]
		}
		return rm
	}

	operator fun minus(b: BitMatrix2): BitMatrix2 {
		val rm = BitMatrix2(width, height)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				rm[i, j] = (this[i, j] xor b[i, j])
			}
		}
		return rm
	}

	operator fun times(b: BitMatrix2): BitMatrix2 {
		val tb = b.transpose()
		val rm = BitMatrix2(b.width, height)
		for (i in 0 until rm.width) {
			val vec = tb.bitSet[i]
			for (j in 0 until rm.height) {
				rm[i, j] = dotProduct(bitSet[j], vec)
			}
		}
		return rm
	}

	operator fun div(b: BitMatrix2): BitMatrix2 {
		return this.times(b.inverse())
	}

	fun mpow(y: BigInteger): BitMatrix2 {
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

	fun inverse(): BitMatrix2 {
		return mpow(BigInteger.valueOf(2).pow(width) - BigInteger.valueOf(2))
	}

	fun random() {
		repeat(bitSet.size) {
			bitSet[it] = srnd.nextLong()
		}
	}

	override fun toString(): String {
		val buf = StringBuilder()
		for (j in 0 until height) {
			for (i in 0 until width) {
				buf.append(if (get(i, j)) 1 else 0)
			}
			buf.append('\n')
		}
		return buf.toString()
	}

	override fun write(dos: DataOutputStream) {
		val bs = BitSet(width * height)
		for (j in 0 until height) {
			for (i in 0 until width) {
				bs.set(j * width + i, get(i, j))
			}
		}
		val ba = bs.toByteArray()
		dos.writeShort(width)
		dos.writeShort(height)
		dos.writeInt(ba.size)
		dos.write(ba)
	}

	override fun equals(other: Any?): Boolean {
		if (other !is BitMatrix2) return false
		return bitSet.contentEquals(other.bitSet)
	}

	override fun hashCode(): Int {
		return bitSet.contentHashCode()
	}

	companion object {
		fun identity(length: Int): BitMatrix2 {
			val m = BitMatrix2(length, length)
			for (i in 0 until length) {
				m[i, i] = true
			}
			return m
		}

		fun dotProduct(a: Long, b: Long): Boolean {
			val v = (a and b)
			return if (v < 0) {
				(-v).countOneBits() + 1
			} else {
				v.countOneBits()
			} % 2 == 1
		}

		fun read(dis: DataInputStream): BitMatrix2 {
			val width = dis.readUnsignedShort()
			val height = dis.readUnsignedShort()
			val arrLength = dis.readInt()
			val ba = ByteArray(arrLength)
			dis.read(ba)
			val bs = BitSet.valueOf(ba)
			val m = BitMatrix2(width, height)
			var count = 0
			for (j in 0 until height) {
				for (i in 0 until width) {
					m[i, j] = bs.get(count)
					++count
				}
			}
			return m
		}

		fun getMaximal(dim: Int, maxRun: Int = 10000): BitMatrix2 {
			val m = BitMatrix2(dim, dim)
			var count = 0
			var found = false
			val ipow = BigInteger.valueOf(2).pow(dim).subtract(BigInteger.ONE)
			while (!found && count < maxRun) {
				m.random()
				val o = m.mpow(ipow)
				if (o.isIdentity) {
					found = true
				}
//				println(count)
				++count
			}
			if (count == maxRun) {
				throw Exception("No optimal matrix found within given iterations")
			}
			return m
		}
	}

	fun transpose(): BitMatrix2 {
		val bm = BitMatrix2(height, width)
		for (j in 0 until height) {
			for (i in 0 until width) {
				bm[j, i] = this[i, j]
			}
		}
		return bm
	}
}
