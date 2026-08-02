package r3.collection

import r3.hash.fromUBase64
import r3.hash.toUBase64
import r3.io.toDataInputStream
import java.io.*

open class DirMap<K, V>(
	private val dir: File,
	private val writeKey: (DataOutputStream, key: K) -> Unit,
	private val readKey: (DataInputStream) -> K,
	private val writeValue: (DataOutputStream, value: V) -> Unit,
	private val readValue: (DataInputStream) -> V
) : MutableSimpleMap<K, V> {
	private val _keys = HashSet<K>()
	private fun getFileName(key: K): String {
		return ByteArrayOutputStream().use {
			DataOutputStream(it).use { dos ->
				writeKey(dos, key)
			}
			it
		}.toByteArray().toUBase64()
	}

	private fun fromFileName(fileName: String): K {
		return fileName.fromUBase64().toDataInputStream().use(readKey)
	}

	init {
		val list = dir.list() ?: arrayOf<String>()
		for (f in list) {
			val k = fromFileName(f)
			_keys.add(k)
		}
	}

	override val size: Int
		get() = _keys.size

	fun getOutputStream(key: K): OutputStream {
		val name = getFileName(key)
		val file = File(dir, name)
		return object : FileOutputStream(file) {
			override fun close() {
				super.close()
				_keys.add(key)
			}
		}
	}

	override fun set(key: K, value: V) {
		val name = getFileName(key)
		val file = File(dir, name)
		DataOutputStream(FileOutputStream(file)).use {
			writeValue(it, value)
		}
		_keys.add(key)
	}

	override fun remove(key: K) {
		val name = getFileName(key)
		val file = File(dir, name)
		file.delete()
		_keys.remove(key)
	}

	override val keys: Set<K>
		get() {
			return HashSet(_keys)
		}

	override fun visit(visitor: (K, V) -> Unit) {
		val list = dir.list() ?: arrayOf<String>()
		for (f in list) {
			val k = fromFileName(f)
			val v = get(k)
			if (v != null) {
				visitor(k, v)
			}
		}
	}

	override fun get(key: K): V? {
		val name = getFileName(key)
		val file = File(dir, name)
		return if (file.exists()) {
			DataInputStream(FileInputStream(file)).use { readValue(it) }
		} else {
			null
		}
	}
}