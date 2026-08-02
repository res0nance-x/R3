package r3.collection

interface MutableSimpleMap<K, V> : SimpleMap<K, V> {
	operator fun set(key: K, value: V)
	fun remove(key: K)
}