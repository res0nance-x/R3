package r3.collection

interface SimpleList<V> {
	fun visit(visitor: (V) -> Unit)
}