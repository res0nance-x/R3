package r3.collection

open class HashSimpleMap<K, T> : MutableSimpleMap<K, T> {
	private val map = LinkedHashMap<K, T>()
	override val keys: Set<K>
		get() = synchronized(map) { map.keys.toSet() }

	override fun get(key: K): T? {
		return synchronized(map) { map[key] }
	}

	override val size: Int
		get() = synchronized(map) { map.size }

	fun clear() {
		synchronized(map) {
			map.clear()
		}
	}

	override fun visit(visitor: (K, T) -> Unit) {
		synchronized(map) {
			for (k in map.keys) {
				map[k]?.also { v -> visitor(k, v) }
			}
		}
	}

	override fun set(key: K, value: T) {
		synchronized(map) {
			map[key] = value
		}
	}

	override fun remove(key: K) {
		key?.also {
			synchronized(map) {
				map.remove(it)
			}
		}
	}
}