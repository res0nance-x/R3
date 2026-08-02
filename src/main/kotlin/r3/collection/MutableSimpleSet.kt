package r3.collection

interface MutableSimpleSet<K> : SimpleSet<K> {
	fun add(key: K)
	fun remove(key: K)
}