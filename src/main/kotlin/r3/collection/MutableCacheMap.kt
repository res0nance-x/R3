package r3.collection

import java.util.*

class MutableCacheMap<K, V>(
	private val load: (k: K) -> V?,
	private val save: (k: K, v: V) -> Unit,
	private val delete: (k: K) -> Unit
) {
	private val map = Collections.synchronizedMap(LinkedHashMap<K, V>())
	val keys: Set<K>
		get() = synchronized(map) { map.keys.toSet() }
	val values: Collection<V>
		get() = synchronized(map) { map.values.toList() }
	val entries: Set<Map.Entry<K, V>>
		get() = synchronized(map) { map.entries.toSet() }
	val size: Int
		get() = map.size

	fun clear() {
		map.clear()
	}

	fun isEmpty(): Boolean {
		return map.isEmpty()
	}

	fun remove(key: K): V? {
		delete(key)
		return map.remove(key)
	}

	operator fun set(key: K, value: V): V? {
		save(key, value)
		return map.put(key, value)
	}

	fun containsKey(key: K): Boolean {
		return map.containsKey(key)
	}

	operator fun get(key: K): V? {
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