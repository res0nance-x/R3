package r3.collection

/**
 * A cache that evicts least recently used items when the total size exceeds maxSize.
 * @param K key type
 * @param V value type
 * @param maxSize maximum total size in bytes
 * @param sizeOf function to get the size of a value in bytes
 */
class MaxSizeCache<K, V>(private val maxSize: Long, private val sizeOf: (V) -> Long) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    private var currentSize = 0L

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        val old = map.remove(key)
        if (old != null) {
            currentSize -= sizeOf(old)
        }
        map[key] = value
        currentSize += sizeOf(value)
        trimToSize()
    }

    @Synchronized
    fun remove(key: K): V? {
        val old = map.remove(key)
        if (old != null) {
            currentSize -= sizeOf(old)
        }
        return old
    }

    @Synchronized
    operator fun contains(key: K): Boolean = map.containsKey(key)

    @Synchronized
    fun clear() {
        map.clear()
        currentSize = 0L
    }

    @Synchronized
    fun keys(): Set<K> = map.keys

    @Synchronized
    fun values(): Collection<V> = map.values

    @Synchronized
    operator fun iterator(): Iterator<Map.Entry<K, V>> = map.entries.iterator()

    @Synchronized
    private fun trimToSize() {
        val it = map.entries.iterator()
        while (currentSize > maxSize && it.hasNext()) {
            val entry = it.next()
            currentSize -= sizeOf(entry.value)
            it.remove()
        }
    }

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun totalSize(): Long = currentSize
}