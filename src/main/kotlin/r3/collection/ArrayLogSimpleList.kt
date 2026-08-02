package r3.collection

class ArrayLogSimpleList<V> : LogSimpleList<V> {
	private val list = ArrayList<V>()
	var listener: (Long, type: UpdateType) -> Unit = { index, type -> }
	override fun add(v: V) {
		list.add(v)
		listener((list.size - 1).toLong(), UpdateType.ADD)
	}

	val size: Long
		get() = list.size.toLong()

	fun get(index: Long): V {
		return list[index.toInt()]
	}

	override fun visit(visitor: (V) -> Unit) {
		var pos = 0
		while (pos < list.size) {
			visitor(list[pos])
			++pos
		}
	}
}