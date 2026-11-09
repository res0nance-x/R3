package r3.pack

import r3.content.Content
import r3.content.FileContent
import r3.io.consistentPath
import java.io.File

class DirPack(val dir: File) : Pack {
	override fun iterator(): Iterator<Content> {
		val list = ArrayList<Content>()
		if (dir.exists()) {
			dir.walk().filter { it.isFile }.forEach { file ->
				val content = FileContent(file = file, root = dir.consistentPath())
				list.add(content)
			}
		}
		return list.iterator()
	}
}