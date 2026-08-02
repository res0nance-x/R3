package r3.math

import r3.hash.hash128
import java.math.BigInteger

/*
Example matrix
	private val arr = longArrayOf(
		197474633, 214282938, 198029145,
		196383522, 206074781, 139311274,
		90385508, 235632615, 199951358
	)
	val prime = 268431407L
	val m = LMatrix(arr, 3, 3, prime)
 */

// Used as generated sequence for stream cipher
// Does not need to be secure, only provide a long unique sequence
class LMatrixSequence(val m: LMatrix, pass: ByteArray) {
	private val v0 = m.mpow(BigInteger(1, pass.hash128()))
	private var v = v0
	private var prevN: Long = 0
	fun getSequence(n: Long): ByteArray {
		if (n - prevN == 1L) {
			v = m.times(v)
			++prevN
		} else {
			v = m.mpow(n).times(v0)
		}
		val a = v[0, 0]
		val b = v[0, 1]
		val c = v[0, 2]
		return byteArrayOf(
			a.toByte(),
			(a ushr 8).toByte(),
			(a ushr 16).toByte(),
			(a ushr 24).toByte(),
			(a ushr 32).toByte(),
			b.toByte(),
			(b ushr 8).toByte(),
			(b ushr 16).toByte(),
			(b ushr 24).toByte(),
			(b ushr 32).toByte(),
			c.toByte(),
			(c ushr 8).toByte(),
			(c ushr 16).toByte(),
			(c ushr 24).toByte(),
			(c ushr 32).toByte(),
			(c ushr 48).toByte()
		)
	}
}