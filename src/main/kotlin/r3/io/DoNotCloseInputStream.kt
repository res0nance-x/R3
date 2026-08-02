package r3.io

import java.io.InputStream

class DoNotCloseInputStream(
	private val istream: InputStream
) : InputStream() {
	override fun read(): Int {
		return istream.read()
	}

	override fun read(b: ByteArray): Int {
		return istream.read(b, 0, b.size)
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		return istream.read(b, off, len)
	}

	override fun skip(n: Long): Long {
		return istream.skip(n)
	}

	override fun available(): Int {
		return istream.available()
	}

	override fun close() {
		// DO NOT CLOSE
	}

	override fun reset() {
		istream.reset()
	}

	override fun mark(readlimit: Int) {
		istream.mark(readlimit)
	}

	override fun markSupported(): Boolean {
		return istream.markSupported()
	}
}
