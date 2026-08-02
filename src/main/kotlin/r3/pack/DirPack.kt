package r3.pack

import r3.content.Content
import r3.content.FileContent
import r3.io.consistentPath
import java.io.File

class DirPack(val dir: File) : Pack {
	val map = LinkedHashMap<String, Content>()

	init {
		if (dir.exists()) {
			dir.walk().filter { it.isFile }.forEach { file ->
				val content = FileContent(file = file, root = dir.consistentPath())
				map[content.path] = content
			}
		}
	}

	override val size: Int
		get() = map.size
	override val keys: Set<String>
		get() = map.keys

	override fun get(key: String): Content? {
		return map[key]
	}

	override fun visit(visitor: (String, Content) -> Unit) {
		map.forEach(visitor)
	}
}