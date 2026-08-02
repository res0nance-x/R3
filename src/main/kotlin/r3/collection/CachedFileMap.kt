package r3.collection

import java.io.*

class CachedFileMap<K, V>(
	val file: File,
	private val writeKey: (DataOutputStream, key: K) -> Unit,
	readKey: (DataInputStream) -> K,
	private val writeValue: (DataOutputStream, value: V) -> Unit,
	readValue: (DataInputStream) -> V
) : MutableSimpleMap<K, V>, Closeable {
	private val dos = DataOutputStream(FileOutputStream(file, true).buffered())
	private val map = LinkedHashMap<K, V>()

	init {
		if (file.exists()) {
			DataInputStream(FileInputStream(file).buffered()).use { dis ->
				while (dis.available() > 0) {
					val key = readKey(dis)
					val removed = dis.read()
					if (removed == 1) {
						map.remove(key!!)
					} else {
						val value = readValue(dis)
						map[key] = value
					}
				}
			}
		}
	}

	override val size: Int
		get() = synchronized(map) { map.size }
	override val keys: Set<K>
		get() = synchronized(map) { HashSet(map.keys) }

	fun containsKey(key: K): Boolean {
		return synchronized(map) { map.containsKey(key) }
	}

	override fun get(key: K): V? {
		return synchronized(map) { map[key] }
	}

	override fun set(key: K, value: V) {
		writeKey(dos, key)
		dos.write(0)
		writeValue(dos, value)
		synchronized(map) {
			map[key] = value
		}
	}

	override fun remove(key: K) {
		synchronized(map) {
			if (key in map) {
				writeKey(dos, key)
				dos.write(1)
				map.remove(key)
			}
		}
	}

	override fun visit(visitor: (K, V) -> Unit) {
		synchronized(map) {
			for (x in map) {
				visitor(x.key, x.value)
			}
		}
	}

	fun flush() {
		dos.flush()
	}

	override fun close() {
		dos.close()
	}
}