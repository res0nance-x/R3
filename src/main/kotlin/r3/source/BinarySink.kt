package r3.source

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class BinarySink(val maxSize: Int = Int.MAX_VALUE, val onFinished: (ByteArray) -> Unit = {}) : Sink {
	private var closed = false
	val baos = ByteArrayOutputStream()
	override fun createOutputStream(): OutputStream {
		return object : OutputStream() {
			override fun write(b: Int) {
				if (baos.size() + 1 > maxSize) {
					throw IOException("maximum in-memory size of $maxSize exceeded")
				}
				baos.write(b)
			}

			override fun write(b: ByteArray, off: Int, len: Int) {
				if (baos.size() + len > maxSize) {
					throw IOException("maximum in-memory size of $maxSize exceeded")
				}
				baos.write(b, off, len)
			}

			override fun close() {
				if (!closed) {
					closed = true
					baos.close()
					onFinished(baos.toByteArray())
				}
				super.close()
			}
		}
	}
}