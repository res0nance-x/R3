package r3.content

import r3.io.consistentPath
import r3.io.log
import r3.source.FileSource
import r3.util.dateTime
import r3.util.humanReadableSize
import java.io.File

class FileContent(
	file: File,
	private val root: String = file.absoluteFile.parentFile?.consistentPath() ?: "",
	override val path: String = file.consistentPath().let { if (it.startsWith(root)) it.substring(root.length) else it },
	override val ext: String = file.extension.lowercase(),
	override val lastModified: Long = file.lastModified()
) : Content, FileSource(file) {
	fun delete() {
		// For efficiency the file may have been moved to the appropriate location rather than copying
		if (file.exists()) {
			if (!file.delete()) {
				log("Was unable to delete file: $file")
			}
		}
	}

	override fun toString(): String {
		return "$path $ext ${length.humanReadableSize()} created:${lastModified.dateTime()}"
	}
}