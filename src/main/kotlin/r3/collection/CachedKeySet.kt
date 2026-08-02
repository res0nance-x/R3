package r3.collection

import java.io.*
import java.util.*

class CachedKeySet<K>(
	val file: File,
	private val writeKey: (DataOutputStream, key: K) -> Unit,
	readKey: (DataInputStream) -> K,
	val comparator: Comparator<K>,
) : MutableSimpleSet<K>, Closeable {
	val dos: DataOutputStream
	private val set = TreeSet<K>(comparator)

	init {
		DataInputStream(FileInputStream(file).buffered()).use { dis ->
			while (dis.available() > 0) {
				val key = readKey(dis)
				val exists = dis.read()
				if (exists > 0) {
					set.add(key)
				} else {
					set.remove(key)
				}
			}
		}
		dos = DataOutputStream(FileOutputStream(file, true).buffered())
	}

	override val keys: SortedSet<K>
		get() = synchronized(set) { TreeSet(set) }

	override fun contains(k: K): Boolean {
		return synchronized(set) { set.contains(k) }
	}

	override fun add(key: K) {
		writeKey(dos, key)
		dos.write(1)
		synchronized(set) {
			set.add(key)
		}
	}

	override fun remove(key: K) {
		if (set.contains(key)) {
			writeKey(dos, key)
			dos.write(0)
			synchronized(set) {
				set.remove(key!!)
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