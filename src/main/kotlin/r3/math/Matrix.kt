package r3.math

import r3.hash.hash256
import r3.io.Writable
import r3.io.serialize
import r3.key.Key256
import r3.util.LGMRandomSequence
import r3.util.RandomSequence
import r3.util.srnd
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger

// Fast modulus for 2^n-1 prime
// where
// int i = k % p;
// is
// int i = (k & p) + (k >> n);
// return (i >= p) ? i - p : i;
class Matrix : Writable {
	val arr: Array<BigInteger>
	val width: Int
	val height: Int
	val prime: BigInteger
	val key256: Key256
		get() {
			return Key256(this.serialize().hash256())
		}
	val isDiagonal: Boolean
		get() {
			for (i in 0 until width) {
				for (j in 0 until height) {
					if (i != j && get(i, j) != ZERO) {
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
						if (get(i, j) != ONE) {
							return false
						}
					} else if (get(i, j) != ZERO) {
						return false
					}
				}
			}
			return true
		}

	constructor(width: Int, height: Int, prime: BigInteger) {
		this.width = width
		this.height = height
		this.arr = Array(width * height) { ZERO }
		this.prime = prime
	}

	constructor(arr: Array<BigInteger>, width: Int, height: Int, prime: BigInteger) {
		this.width = width
		this.height = height
		this.arr = arr
		this.prime = prime
	}

	operator fun plus(b: Matrix): Matrix {
		val rm = Matrix(width, height, prime)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				rm[i, j] = (this[i, j] + b[i, j]).mod(prime)
			}
		}
		return rm
	}

	operator fun minus(b: Matrix): Matrix {
		val rm = Matrix(width, height, prime)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				rm[i, j] = (this[i, j] + prime - b[i, j]).mod(prime)
			}
		}
		return rm
	}

	operator fun minusAssign(b: Matrix) {
		for (i in 0 until width) {
			for (j in 0 until height) {
				this[i, j] = (this[i, j] + prime - b[i, j]).mod(prime)
			}
		}
	}

	operator fun plusAssign(b: Matrix) {
		for (i in 0 until width) {
			for (j in 0 until height) {
				this[i, j] = (this[i, j] + b[i, j]).mod(prime)
			}
		}
	}

	private fun rawPlus(b: Matrix): Matrix {
		val rm = Matrix(width, height, prime)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				rm[i, j] = this[i, j] + b[i, j]
			}
		}
		return rm
	}

	operator fun times(b: Matrix): Matrix {
		val rm = Matrix(b.width, height, prime)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				var value: BigInteger = ZERO
				for (k in 0 until b.height) {
					value += this[k, j] * b[i, k]
				}
				rm[i, j] = value.mod(prime)
			}
		}
		return rm
	}

	operator fun div(b: Matrix): Matrix {
		return this.times(b.inverse())
	}

	private fun rawTimes(b: Matrix): Matrix {
		val rm = Matrix(b.width, height, prime)
		for (i in 0 until rm.width) {
			for (j in 0 until rm.height) {
				var value: BigInteger = ZERO
				for (k in 0 until b.height) {
					value += this[k, j] * b[i, k]
				}
				rm[i, j] = value
			}
		}
		return rm
	}

	fun mpow(y: BigInteger): Matrix {
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

	fun inverse(): Matrix {
		return mpow(prime.pow(width).subtract(TWO))
	}

	fun mod(): Matrix {
		val rm = Matrix(width, height, prime)
		for ((i, v) in arr.withIndex()) {
			rm.arr[i] = v % prime
		}
		return rm
	}

	fun isMaximal(factors: Array<BigInteger>): Boolean {
		val perm = permutations(factors)
		val o = mpow(prime.pow(width).subtract(ONE))
		var found = false
		if (o.isIdentity) {
			found = true
			for (i in perm.indices) {
				if (mpow(perm[i]).mod().isIdentity) {
					found = false
					break
				}
			}
		}
		return found
	}

	fun inversionPower(): BigInteger {
		return prime.pow(width).subtract(ONE)
	}

	fun copy(): Matrix {
		return Matrix(arr.copyOf(), width, height, prime)
	}

	operator fun get(i: Int, j: Int): BigInteger {
		return arr[j * width + i]
	}

	operator fun set(i: Int, j: Int, value: BigInteger) {
		arr[j * width + i] = value
	}

	fun calculateDensity(factors: Array<BigInteger>): Double {
		val MAXRUN = 1000
		val arr = Array(width * width) { ZERO }
		val perm = permutations(factors)
		var m: Matrix?
		var o: Matrix
		var count = 0
		var foundCount = 0
		var found = false
		while (count < MAXRUN) {
			for (i in arr.indices) {
				arr[i] = BigInteger(1, srnd.getByteArray(prime.bitLength()))
			}
			m = Matrix(arr, width, width, prime)
			o = m.mpow(prime.pow(width).subtract(ONE)).mod()
			if (o.isIdentity) {
				found = true
				for (i in perm.indices) {
					if (m.mpow(perm[i]).mod().isIdentity) {
						found = false
					}
				}
			}
			if (found) {
				++foundCount
			}
			found = false
			++count
		}
		return foundCount.toDouble() / count
	}

	override fun write(dos: DataOutputStream) {
		dos.write(width)
		dos.write(height)
		for (x in arr) {
			val barr = x.toByteArray()
			dos.write(barr.size)
			dos.write(barr)
		}
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

	fun random() {
		for (i in arr.indices) {
			arr[i] = BigInteger(1, srnd.getByteArray(prime.bitLength()))
		}
	}

	fun random(rnd: RandomSequence) {
		for (i in arr.indices) {
			arr[i] = BigInteger(1, rnd.getByteArray(prime.bitLength()))
		}
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (javaClass != other?.javaClass) {
			return false
		}

		other as Matrix
		return arr.contentEquals(other.arr)
	}

	override fun hashCode(): Int {
		return arr.contentHashCode()
	}

	companion object {
		fun identity(length: Int, prime: BigInteger): Matrix {
			val m = Matrix(length, length, prime)
			for (i in 0 until length) {
				m[i, i] = ONE
			}
			return m
		}

		fun read(dis: DataInputStream, prime: BigInteger): Matrix {
			val w = dis.read() and 0xFF
			val h = dis.read() and 0xFF
			val a = Array(w * h) {
				val len = dis.read() and 0xFF
				val ba = ByteArray(len)
				dis.read(ba)
				BigInteger(1, ba)
			}
			return Matrix(a, w, h, prime)
		}

		fun getMaximal(
			pass: ByteArray,
			dim: Int,
			prime: BigInteger,
			factors: Array<BigInteger>,
			maxRun: Int = 10000
		): Matrix {
			val perm = permutations(factors)
			val m = Matrix(dim, dim, prime)
			var count = 0
			var found = false
			val seq = LGMRandomSequence(pass)
			while (!found && count < maxRun) {
				m.random(seq)
				val o = m.mpow(prime.pow(dim).subtract(ONE))
				if (o.isIdentity) {
					found = true
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
				throw Exception("No optimal matrix found for dim $dim and base.getPrime $prime within given iterations")
			}
			return m
		}

		fun getMaximal(dim: Int, prime: BigInteger, factors: Array<BigInteger>, maxRun: Int = 10000): Matrix {
			val perm = permutations(factors)
			val m = Matrix(dim, dim, prime)
			var count = 0
			var found = false
			while (!found && count < maxRun) {
				m.random()
				val o = m.mpow(prime.pow(dim).subtract(ONE))
				if (o.isIdentity) {
					found = true
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
				throw Exception("No optimal matrix found for dim $dim and base.getPrime $prime within given iterations")
			}
			return m
		}
	}
}

// Can use this to find a good prime that has minimal factors
// Factors = {2,(p−1)/2,(1+p+p^2)}
// (p-1)*(1+p+p^2) = p^3-1
fun findM3P(startPrime: BigInteger): Pair<BigInteger, Matrix> {
	var p = startPrime
	while (true) {
		if (p.isProbablePrime(10)) {
			val x = p.pow(2) + p + ONE
			val y = (p - ONE).divide(TWO)
			if (x.isProbablePrime(10) && y.isProbablePrime(10) &&
				p.isProbablePrime(100) && x.isProbablePrime(100) && y.isProbablePrime(100)
			) {
				break
			}
		}
		p -= TWO
	}
	val a = p.pow(2) + p + ONE
	val b = (p - ONE).divide(TWO)
	val factors = arrayOf(TWO, a, b)
	val m = Matrix.getMaximal(5, p, factors)
	return Pair(p, m)
}

// Factors = {2,(p−1)/2,(1+p+p^2+p^3+p^4)}
// (p-1)*(1+p+p^2+p^3+p^4) = p^5-1
fun findM5P(startPrime: BigInteger): Pair<BigInteger, Matrix> {
	var p = startPrime
	while (true) {
		if (p.isProbablePrime(10)) {
			val x = p.pow(4) + p.pow(3) + p.pow(2) + p + ONE
			val y = (p - ONE).divide(TWO)
			if (x.isProbablePrime(10) && y.isProbablePrime(10) &&
				p.isProbablePrime(100) && x.isProbablePrime(100) && y.isProbablePrime(100)
			) {
				break
			}
		}
		p -= TWO
	}
	val a = p.pow(4) + p.pow(3) + p.pow(2) + p + ONE
	val b = (p - ONE).divide(TWO)
	val factors = arrayOf(TWO, a, b)
	val m = Matrix.getMaximal(5, p, factors)
	return Pair(p, m)
}