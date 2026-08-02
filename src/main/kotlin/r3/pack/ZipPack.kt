package r3.pack

import r3.collection.HashSimpleMap
import r3.content.Content
import r3.content.ZipEntryContent
import java.io.File
import java.util.zip.ZipFile

class ZipPack(file: File) : Pack {
	private val map = HashSimpleMap<String, Content>()

	init {
		val zip = ZipFile(file)
		for (x in zip.entries()) {
			if (!x.isDirectory) {
				val c = ZipEntryContent(zip, x)
				map[c.path] = c
			}
		}
	}

	override val size: Int
		get() = map.size
	override val keys: Set<String>
		get() = HashSet(map.keys)

	override fun get(key: String): Content? {
		return map[key]
	}

	override fun visit(visitor: (String, Content) -> Unit) {
		map.visit(visitor)
	}
}