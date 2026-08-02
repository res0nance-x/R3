package r3.collection

interface SimpleMap<K, V> {
	val size: Int
	val keys: Set<K>
	operator fun get(key: K): V?
	fun visit(visitor: (K, V) -> Unit)
}