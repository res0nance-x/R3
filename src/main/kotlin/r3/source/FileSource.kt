package r3.source

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

open class FileSource(val file: File) : Source {
	override val length: Long
		get() = file.length()

	override fun createInputStream(): InputStream {
		return FileInputStream(file)
	}
}

fun File.createSource(): FileSource {
	return FileSource(this)
}