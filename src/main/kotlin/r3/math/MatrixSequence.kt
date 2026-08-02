package r3.math

import r3.hash.hash256
import java.math.BigInteger

class MatrixSequence(pass: ByteArray) {
	constructor(pass: String) : this(pass.toByteArray())

	private val iv = pass.hash256()
	private val arr = arrayOf(
		"221637679345536400762259729387314260868",
		"94689939875813367804292055368069571498",
		"78287349673697843103879912727862501900",
		"38096289613070474010258691170528786502",
		"235915103925536791340427791370937628538",
		"237839176654266471022870502583889305434",
		"26748473151630252255209341518155410555",
		"21904792423970862677359236409954014817",
		"239973894956684111531865970478037288139"
	).map {
		BigInteger(it)
	}.toTypedArray()
	private val m: Matrix = Matrix(arr, 3, 3, prime128)
	private var prevN: Long = 0
	private val v0: Matrix = m.mpow(BigInteger(1, iv)).times(
		Matrix(
			arrayOf(ZERO, ZERO, BigInteger(1, iv)),
			1, 3, prime128
		)
	)
	private var v = v0
	fun getSequence(n: Long): ByteArray {
		if (n - prevN == 1L) {
			v = m.times(v)
			++prevN
		} else {
			v = m.mpow(BigInteger.valueOf(n)).times(v0)
		}
		val vb = v[0, 2].toByteArray()
		return when {
			vb.size == 16 -> {
				vb
			}

			vb.size > 16 -> {
				vb.copyOfRange(0, 16)
			}

			else -> {
				val ba = ByteArray(16)
				System.arraycopy(vb, 0, ba, 16 - vb.size, vb.size)
				ba
			}
		}
	}
}