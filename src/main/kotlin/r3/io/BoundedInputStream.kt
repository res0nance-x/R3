package r3.io

import java.io.InputStream

class BoundedInputStream(
	private val istream: InputStream,
	private val max: Long = Long.MAX_VALUE
) : InputStream() {
	private var pos: Long = 0
	private var mark = -1L
	override fun read(): Int {
		if (max in 0..pos) {
			return -1
		}
		val result = istream.read()
		pos++
		return result
	}

	override fun read(b: ByteArray): Int {
		return this.read(b, 0, b.size)
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		if (max in 0..pos) {
			return -1
		}
		val maxRead = if (max >= 0) Math.min(len.toLong(), max - pos) else len.toLong()
		val bytesRead = istream.read(b, off, maxRead.toInt())
		if (bytesRead == -1) {
			return -1
		}
		pos += bytesRead.toLong()
		return bytesRead
	}

	override fun skip(n: Long): Long {
		val toSkip = if (max >= 0) n.coerceAtMost(max - pos) else n
		val skippedBytes = istream.skip(toSkip)
		pos += skippedBytes
		return skippedBytes
	}

	override fun available(): Int {
		return if (max in 0..pos) {
			0
		} else istream.available()
	}

	override fun toString(): String {
		return istream.toString()
	}

	override fun close() {
		istream.close()
	}

	override fun reset() {
		istream.reset()
		pos = mark
	}

	override fun mark(readlimit: Int) {
		istream.mark(readlimit)
		mark = pos
	}

	override fun markSupported(): Boolean {
		return istream.markSupported()
	}
}
