package r3.io

import java.io.InputStream

class CountingInputStream(val istream: InputStream) : InputStream() {
	var count = 0L
	override fun available(): Int {
		return istream.available()
	}

	override fun read(): Int {
		val r = istream.read()
		if (r > -1) {
			++count
		}
		return r
	}

	override fun read(b: ByteArray): Int {
		val r = istream.read(b)
		if (r > -1) {
			count += r
		}
		return super.read(b)
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		val r = istream.read(b, off, len)
		if (r > -1) {
			count += r
		}
		return r
	}

	override fun skip(n: Long): Long {
		val r = istream.skip(n)
		count += r
		return r
	}

	override fun close() {
		istream.close()
	}
}