package r3.pack

import r3.content.Content
import r3.content.FileContent
import r3.io.consistentPath
import java.io.File

class DirPack(val dir: File) : Pack {
	private val list = ArrayList<Content>()

	init {
		if (dir.exists()) {
			dir.walk().filter { it.isFile }.forEach { file ->
				val content = FileContent(file = file, root = dir.consistentPath())
				list.add(content)
			}
		}
	}

	override val size: Int = list.size
	override fun iterator(): Iterator<Content> {
		return list.iterator()
	}
}