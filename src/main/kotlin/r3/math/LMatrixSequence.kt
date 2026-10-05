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
	private val m00 = m[0, 0]; private val m10 = m[1, 0]; private val m20 = m[2, 0]
	private val m01 = m[0, 1]; private val m11 = m[1, 1]; private val m21 = m[2, 1]
	private val m02 = m[0, 2]; private val m12 = m[1, 2]; private val m22 = m[2, 2]
	private val prime = m.prime

	private val v0 = m.mpow(BigInteger(1, pass.hash128()))
	private var curX0 = v0[0, 0]
	private var curX1 = v0[0, 1]
	private var curX2 = v0[0, 2]
	private var prevN: Long = 0

	fun getSequence(n: Long): ByteArray {
		val arr = ByteArray(16)
		fillSequence(n, arr, 0)
		return arr
	}

	fun fillSequence(n: Long, dest: ByteArray, offset: Int) {
		if (n - prevN == 1L) {
			val nx0 = (m00 * curX0 + m10 * curX1 + m20 * curX2) % prime
			val nx1 = (m01 * curX0 + m11 * curX1 + m21 * curX2) % prime
			val nx2 = (m02 * curX0 + m12 * curX1 + m22 * curX2) % prime
			curX0 = nx0
			curX1 = nx1
			curX2 = nx2
			++prevN
		} else {
			val v = m.mpow(n).times(v0)
			curX0 = v[0, 0]
			curX1 = v[0, 1]
			curX2 = v[0, 2]
			prevN = n // Update prevN so subsequent sequential calls use fast path!
		}

		dest[offset + 0] = curX0.toByte()
		dest[offset + 1] = (curX0 ushr 8).toByte()
		dest[offset + 2] = (curX0 ushr 16).toByte()
		dest[offset + 3] = (curX0 ushr 24).toByte()
		dest[offset + 4] = (curX0 ushr 32).toByte()
		dest[offset + 5] = curX1.toByte()
		dest[offset + 6] = (curX1 ushr 8).toByte()
		dest[offset + 7] = (curX1 ushr 16).toByte()
		dest[offset + 8] = (curX1 ushr 24).toByte()
		dest[offset + 9] = (curX1 ushr 32).toByte()
		dest[offset + 10] = curX2.toByte()
		dest[offset + 11] = (curX2 ushr 8).toByte()
		dest[offset + 12] = (curX2 ushr 16).toByte()
		dest[offset + 13] = (curX2 ushr 24).toByte()
		dest[offset + 14] = (curX2 ushr 32).toByte()
		dest[offset + 15] = (curX2 ushr 48).toByte()
	}

	fun fillBlock(startPos: Long, dest: ByteArray, offset: Int, count16: Int) {
		var pos = startPos
		var curOffset = offset
		repeat(count16) {
			fillSequence(pos++, dest, curOffset)
			curOffset += 16
		}
	}
}