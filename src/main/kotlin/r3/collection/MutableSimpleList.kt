package r3.collection

interface MutableSimpleList<V> : SimpleList<V> {
	fun add(v: V)
}