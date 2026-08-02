package r3.encryption

import r3.math.EncryptedSequence
import r3.source.Sink
import r3.source.createSink
import java.io.File
import java.io.OutputStream

class EncryptedSink(val seq: EncryptedSequence, val sink: Sink, val initialPos: Long = 0L) : Sink {
	override fun createOutputStream(): OutputStream {
		return EncryptedContinuousOutputStream(seq, sink.createOutputStream(), initialPos)
	}
}

fun File.createEncryptedSink(seq: EncryptedSequence, initialPos: Long = 0L): EncryptedSink {
	return EncryptedSink(seq, this.createSink(), initialPos)
}