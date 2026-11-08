package r3.encryption

import r3.math.EncryptedSequence
import r3.source.Source
import r3.source.createSource
import java.io.File
import java.io.InputStream

class EncryptedSource(val seq: EncryptedSequence, val source: Source) : Source, AutoCloseable {
	override fun createInputStream(): InputStream {
		val istream = source.createInputStream()
		return EncryptedContinuousInputStream(seq, istream, autoCloseSeq = false)
	}

	override val length: Long
		get() = source.length

	override fun close() {
		seq.close()
	}
}

fun File.createEncryptedSource(seq: EncryptedSequence): EncryptedSource {
	return EncryptedSource(seq, this.createSource())
}
