package r3.collection

class CacheMap<K, V>(val load: (key: K) -> V?) : Map<K, V> {
	private val map = HashMap<K, V>()
	override val entries: Set<Map.Entry<K, V>>
		get() = map.entries
	override val keys: Set<K>
		get() = map.keys
	override val size: Int
		get() = map.size
	override val values: Collection<V>
		get() = map.values

	override fun isEmpty(): Boolean {
		return map.isEmpty()
	}

	override fun containsValue(value: V): Boolean {
		return map.containsValue(value)
	}

	override fun containsKey(key: K): Boolean {
		return map.containsKey(key)
	}

	override operator fun get(key: K): V? {
		if (map.containsKey(key)) {
			return map[key]
		}
		val value = load(key)
		if (value != null) {
			map[key] = value
		}
		return value
	}
}