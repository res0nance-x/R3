package r3.util

// Automatically closes expired objects that implement AutoCloseable
class Cache<K, V>(val maxSize: Int) :
	LinkedHashMap<K, V>(maxSize * 10 / 7, 0.7f, true) {
	override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean {
		val remove = size > maxSize
		val value = eldest.value
		if (remove && value is AutoCloseable) {
			value.close()
		}
		return remove
	}
}