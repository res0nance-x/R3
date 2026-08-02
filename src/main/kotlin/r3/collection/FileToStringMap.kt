package r3.collection

import r3.io.log
import java.io.File

class FileToStringMap(val rootDir: File) : MutableSimpleMap<String, String> {
	private val map = LinkedHashMap<String, String>()
	override val size: Int
		get() = map.size
	override val keys: Set<String>
		get() = map.keys

	override fun get(key: String): String? {
		val normalized = key.replace('\\', '/')
		if (!map.contains(normalized)) {
			try {
				val text = rootDir.resolve(normalized).readText()
				map[normalized] = text
			} catch (_: Exception) {
				log("FileToStringMap: Error reading file for key '$key'")
			}
		}
		return map[normalized]
	}

	override fun visit(visitor: (String, String) -> Unit) {
		map.entries.forEach { (key, value) ->
			visitor(key, value)
		}
	}

	override fun set(key: String, value: String) {
		map[key.replace('\\', '/')] = value
	}

	override fun remove(key: String) {
		map.remove(key.replace('\\', '/'))
	}
}