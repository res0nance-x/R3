package r3.source

import java.io.ByteArrayInputStream

open class BinarySource(val arr: ByteArray, override val length: Long = arr.size.toLong()) : Source {
	override fun createInputStream(): ByteArrayInputStream {
		return ByteArrayInputStream(arr)
	}
}

fun ByteArray.toSource(): Source {
	return BinarySource(this)
}