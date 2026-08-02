package r3.encryption

import r3.io.readFixedBytes
import r3.math.EncryptedSequence
import java.io.InputStream

// This is a stream cipher that can be efficiently skipped to a position
class EncryptedContinuousInputStream(val seq: EncryptedSequence, val istream: InputStream) : InputStream() {
	private var pos = 0L
	override fun read(b: ByteArray, off: Int, len: Int): Int {
		val n = istream.readFixedBytes(b, off, len)
		repeat(n) {
			b[off + it] = (b[off + it].toInt() xor (seq.get(pos++).toInt() and 0xFF)).toByte()
		}
		if (n == 0) {
			return -1
		}
		return n
	}

	override fun read(): Int {
		val b = istream.read()
		if (b < 0) {
			return -1
		}
		return b xor (seq.get(pos++).toInt() and 0xFF)
	}

	override fun skip(n: Long): Long {
		val skip = istream.skip(n)
		pos += skip
		return skip
	}

	override fun available(): Int {
		return istream.available()
	}

	override fun close() {
		istream.close()
	}
}
