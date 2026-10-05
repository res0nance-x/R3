package r3.encryption

import r3.math.EncryptedSequence
import r3.pke.Password256
import java.io.OutputStream

class EncryptedContinuousOutputStream(val seq: EncryptedSequence, val ostream: OutputStream, initialPos: Long = 0L) : OutputStream() {
	private var pos = initialPos

	constructor(pass: Password256, ostream: OutputStream, initialPos: Long = 0L) : this(EncryptedSequence.createSequence(pass), ostream, initialPos)

	override fun write(b: ByteArray, off: Int, len: Int) {
		val arr = ByteArray(len)
		System.arraycopy(b, off, arr, 0, len)
		seq.xor(arr, 0, len, pos)
		pos += len
		ostream.write(arr)
	}

	override fun write(b: Int) {
		ostream.write(b xor (seq.get(pos++).toInt() and 0xFF))
	}

	override fun close() {
		try {
			seq.close()
		} finally {
			ostream.close()
		}
	}
}
