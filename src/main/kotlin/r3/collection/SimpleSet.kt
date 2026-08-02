package r3.collection

import java.util.*

interface SimpleSet<K> {
	val keys: SortedSet<K>
	fun contains(k: K): Boolean
}