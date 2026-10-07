package r3.io

import java.io.FilterInputStream
import java.io.InputStream

class CountingInputStream(val istream: InputStream) : FilterInputStream(istream) {
	var count: Long = 0L

	override fun read(): Int {
		val r = super.read()
		if (r != -1) {
			count++
		}
		return r
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		val r = super.read(b, off, len)
		if (r > 0) {
			count += r
		}
		return r
	}

	override fun skip(n: Long): Long {
		val r = super.skip(n)
		if (r > 0) {
			count += r
		}
		return r
	}

	fun resetCount() {
		count = 0L
	}
}
