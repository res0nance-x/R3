package r3.io

import java.io.File
import java.math.BigInteger
import java.util.regex.Pattern
import kotlin.math.min

class NaturalStringComparator : Comparator<String> {
	override fun compare(a: String, b: String): Int {
		val split1 = NUMBERS.split(a)
		val split2 = NUMBERS.split(b)
		for (i in 0 until min(split1.size, split2.size)) {
			val c1 = split1[i][0]
			val c2 = split2[i][0]
			var cmp = 0
			// If both segments start with a digit, sort them numerically using
			// BigInteger to stay safe
			if (c1 in '0'..'9' && c2.code >= 0 && c2 <= '9') cmp = BigInteger(split1[i]).compareTo(
				BigInteger(
					split2[i]
				)
			)
			// If we haven't sorted numerically before, or if numeric sorting yielded
			// equality (e.r3.g 007 and 7) then sort lexicographically
			if (cmp == 0) cmp = split1[i].compareTo(split2[i])
			// Abort once some prefix has unequal ordering
			if (cmp != 0) return cmp
		}
		// If we reach this, then both strings have equally ordered prefixes, but
		// maybe one string is longer than the other (i.e. has more segments)
		return split1.size - split2.size
	}

	companion object {
		private val NUMBERS = Pattern.compile("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)")
	}
}

class NaturalFileComparator : Comparator<File> {
	val naturalStringComparator = NaturalStringComparator()
	override fun compare(fa: File, fb: File): Int {
		val a = fa.absolutePath
		val b = fb.absolutePath
		return naturalStringComparator.compare(a, b)
	}
}