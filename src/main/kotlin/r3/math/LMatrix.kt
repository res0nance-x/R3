package r3.math

import r3.io.Writable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger

class LMatrix : Writable {
	val arr: LongArray
	val width: Int
	val height: Int
	val prime: Long
	val isDiagonal: Boolean
		get() {
			for (i in 0 until width) {
				for (j in 0 until height) {
					if (i != j && get(i, j) != 0L) {
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
						if (get(i, j) != 1L) {
							return false
						}
					} else if (get(i, j) != 0L) {
						return false
					}
				}
			}
			return true
		}

	constructor(width: Int, height: Int, prime: Long) {
		this.width = width
		this.height = height
		this.arr = LongArray(width * height)
		this.prime = prime
	}

	constructor(arr: LongArray, width: Int, height: Int, prime: Long) {
		this.width = width
		this.height = height
		this.arr = arr
		this.prime = prime
	}

	operator fun plus(b: LMatrix): LMatrix {
		val rm = LMatrix(width, height, prime)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				rm[i, j] = (this[i, j] + b[i, j]) % prime
			}
		}
		return rm
	}

	operator fun minus(b: LMatrix): LMatrix {
		val rm = LMatrix(width, height, prime)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				rm[i, j] = (this[i, j] + prime - b[i, j]) % prime
			}
		}
		return rm
	}

	operator fun minusAssign(b: LMatrix) {
		for (i in 0 until width) {
			for (j in 0 until height) {
				this[i, j] = (this[i, j] + prime - b[i, j]) % prime
			}
		}
	}

	operator fun plusAssign(b: LMatrix) {
		for (i in 0 until width) {
			for (j in 0 until height) {
				this[i, j] = (this[i, j] + b[i, j]) % prime
			}
		}
	}

	private fun rawPlus(b: LMatrix): LMatrix {
		val rm = LMatrix(width, height, prime)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				rm[i, j] = this[i, j] + b[i, j]
			}
		}
		return rm
	}

	operator fun times(b: LMatrix): LMatrix {
		val rm = LMatrix(b.width, height, prime)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				var value = 0L
				for (k in 0 until b.height) {
					value += this[k, j] * b[i, k]
				}
				rm[i, j] = value % prime
			}
		}
		return rm
	}

	private fun rawTimes(b: LMatrix): LMatrix {
		val rm = LMatrix(b.width, height, prime)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				var value = 0L
				for (k in 0 until b.height) {
					value += this[k, j] * b[i, k]
				}
				rm[i, j] = value
			}
		}
		return rm
	}

	fun mpow(n: Long): LMatrix {
		val y = BigInteger.valueOf(n)
		val len = y.bitLength()
		var v = identity(height, prime)
		var sq = identity(height, prime)
		for (i in 0 until len) {
			if (i == 0) {
				sq = this
			} else {
				sq = sq.rawTimes(sq).mod()
			}
			if (y.testBit(i)) {
				v = sq.rawTimes(v).mod()
			}
		}
		return v
	}

	fun mpow(y: BigInteger): LMatrix {
		val len = y.bitLength()
		var v = identity(height, prime)
		var sq = identity(height, prime)
		for (i in 0 until len) {
			if (i == 0) {
				sq = this
			} else {
				sq = sq.rawTimes(sq).mod()
			}
			if (y.testBit(i)) {
				v = sq.rawTimes(v).mod()
			}
		}
		return v
	}

	fun mod(): LMatrix {
		val rm = LMatrix(width, height, prime)
		for ((i, v) in arr.withIndex()) {
			rm.arr[i] = v % prime
		}
		return rm
	}

	operator fun get(i: Int, j: Int): Long {
		return arr[j * width + i]
	}

	operator fun set(i: Int, j: Int, value: Long) {
		arr[j * width + i] = value
	}

	override fun toString(): String {
		val buf = StringBuilder()
		for (j in 0 until height) {
			for (i in 0 until width) {
				buf.append(get(i, j)).append(',')
			}
			buf.append('\n')
		}
		return buf.toString()
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (javaClass != other?.javaClass) {
			return false
		}

		other as LMatrix
		return arr.contentEquals(other.arr)
	}

	override fun hashCode(): Int {
		return arr.contentHashCode()
	}

	override fun write(dos: DataOutputStream) {
		dos.write(width)
		dos.write(height)
		for (x in arr) {
			dos.writeInt(x.toInt())
		}
	}

	companion object {
		fun read(dis: DataInputStream, prime: Long): LMatrix {
			val w = dis.read() and 0xFF
			val h = dis.read() and 0xFF
			val a = LongArray(w * h) {
				dis.readInt().toLong()
			}
			return LMatrix(a, w, h, prime)
		}

		fun identity(length: Int, prime: Long): LMatrix {
			val m = LMatrix(length, length, prime)
			for (i in 0 until length) {
				m[i, i] = 1L
			}
			return m
		}
	}
}
