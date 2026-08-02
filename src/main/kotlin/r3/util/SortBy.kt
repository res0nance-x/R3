package r3.util

import r3.io.NaturalStringComparator

class SortBy(private val index: List<Int>, private val reverse: Boolean) : Comparator<List<String>> {
	private val naturalStringComparator = NaturalStringComparator()
	private val ilist = index
	override fun compare(a: List<String>, b: List<String>): Int {
		var c = 0
		for (x in ilist) {
			c = naturalStringComparator.compare(a[x], b[x])
			if (reverse) {
				c = -c
			}
			if (c != 0) {
				break
			}
		}
		return c
	}
}

fun createFilter(key: List<String>, toMatch: (List<String>) -> String): (List<String>) -> Boolean {
	val include = ArrayList<String>()
	val exclude = ArrayList<String>()
	for (x in key) {
		if (x.startsWith('-')) {
			exclude.add(x.substring(1))
		} else {
			include.add(x)
		}
	}
	return {
		val word = toMatch(it)
		var match = true
		for (x in include) {
			if (!word.contains(x)) {
				match = false
				break
			}
		}
		for (x in exclude) {
			if (word.contains(x)) {
				match = false
				break
			}
		}
		match
	}
}
