package r3.math

import java.math.BigInteger
import java.util.*

// 128 bit optimal prime
val prime128 = BigInteger("340282366920938463463374607431767977823")

// 256 bit optimal prime
val prime256 = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913126843619")

// 512 bit optimal prime
val prime512 =
	BigInteger("13407807929942597099574024998205846127479365820592393377723561443721764030073546976801874298166903427690031858186486050853753882811946569946433648973826543")
val ZERO: BigInteger = BigInteger.ZERO
val ONE: BigInteger = BigInteger.ONE
val TWO: BigInteger = BigInteger.valueOf(2)
fun permutations(factor: Array<BigInteger>): Array<BigInteger> {
	val arr = IntArray(factor.size)
	for (i in arr.indices) {
		arr[i] = i
	}
	val listperm = permutations(arr)
	val set = TreeSet<BigInteger>()
	for (i in listperm.indices) {
		val x = listperm[i]
		var bi = ONE
		for (j in x.indices) {
			bi = bi.multiply(factor[x[j]])
		}
		set.add(bi)
	}
	return set.toTypedArray()
}

private fun permutations(factors: IntArray): List<IntArray> {
	val s = IntArray(factors.size)
	for (i in s.indices) {
		s[i] = i
	}
	val list = ArrayList<IntArray>()
	for (i in 1 until s.size) {
		list.addAll(subSets(s, i))
	}
	return list
}

private fun recursiveSubsets(sets: MutableList<IntArray>, s: IntArray, k: Int, t: IntArray, q: Int, r: Int) {
	if (q == k) {
		val ss = IntArray(k)
		System.arraycopy(t, 0, ss, 0, k)
		sets.add(ss)
	} else {
		for (i in r until s.size) {
			t[q] = s[i]
			recursiveSubsets(sets, s, k, t, q + 1, i + 1)
		}
	}
}

private fun subSets(s: IntArray, k: Int): List<IntArray> {
	val list = ArrayList<IntArray>()
	val t = IntArray(s.size)
	recursiveSubsets(list, s, k, t, 0, 0)
	return list
}
