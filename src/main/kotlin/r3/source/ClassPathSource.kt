package r3.source

import r3.io.readAll
import java.io.InputStream

class ClassPathSource(val path: String) : Source {
	override fun createInputStream(): InputStream {
		return ClassPathSource::class.java.getResourceAsStream(path) ?: error("$path not found")
	}

	override val length: Long
		get() = createInputStream().readAll().size.toLong()
}