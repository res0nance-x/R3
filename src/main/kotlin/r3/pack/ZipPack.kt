package r3.pack

import r3.content.Content
import r3.content.ZipEntryContent
import java.io.File
import java.util.zip.ZipFile

class ZipPack(file: File) : Pack {
	private val list = ArrayList<Content>()

	init {
		val zip = ZipFile(file)
		for (x in zip.entries()) {
			if (!x.isDirectory) {
				val c = ZipEntryContent(zip, x)
				list.add(c)
			}
		}
	}

	override val size: Int = list.size
	override fun iterator(): Iterator<Content> {
		return list.iterator()
	}
}