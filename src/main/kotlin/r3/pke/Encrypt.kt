package r3.pke

import r3.content.Content
import r3.content.ContentMeta
import r3.encryption.EncryptedContinuousInputStream
import r3.encryption.EncryptedContinuousOutputStream
import r3.math.EncryptedSequence
import r3.source.Sink
import r3.source.Source
import java.io.*

object Encrypt {
	fun encrypt(pass: Password256, source: Source, sink: Sink) {
		val seq = EncryptedSequence.createSequence(pass)
		val ostream = sink.createOutputStream().buffered(r3.io.DEFAULT_COPY_BUFFER_SIZE)
		return EncryptedContinuousOutputStream(seq, ostream).use { out ->
			source.createInputStream().buffered(r3.io.DEFAULT_COPY_BUFFER_SIZE).use { istream ->
				istream.copyTo(out, r3.io.DEFAULT_COPY_BUFFER_SIZE)
			}
		}
	}

	fun encrypt(pass: Password256, data: ByteArray): ByteArray {
		val seq = EncryptedSequence.createSequence(pass)
		val baos = ByteArrayOutputStream()
		EncryptedContinuousOutputStream(seq, baos).use { out ->
			ByteArrayInputStream(data).use { istream ->
				istream.copyTo(out)
			}
		}
		return baos.toByteArray()
	}

	fun decrypt(pass: Password256, data: ByteArray): ByteArray {
		val seq = EncryptedSequence.createSequence(pass)
		val baos = ByteArrayOutputStream()
		EncryptedContinuousInputStream(seq, ByteArrayInputStream(data)).use { istream ->
			baos.use { ostream ->
				istream.copyTo(ostream)
			}
		}
		return baos.toByteArray()
	}

	fun decrypt(pass: Password256, source: Source, sink: Sink) {
		val seq = EncryptedSequence.createSequence(pass)
		val istream = source.createInputStream().buffered(r3.io.DEFAULT_COPY_BUFFER_SIZE)
		EncryptedContinuousInputStream(seq, istream).use { eistream ->
			sink.createOutputStream().buffered(r3.io.DEFAULT_COPY_BUFFER_SIZE).use { ostream ->
				eistream.copyTo(ostream, r3.io.DEFAULT_COPY_BUFFER_SIZE)
			}
		}
	}
}

fun Source.encrypt(pass: Password256, sink: Sink) {
	Encrypt.encrypt(pass, this, sink)
}

fun Source.decrypt(pass: Password256, name: String, type: String, created: Long): Content {
	return EncryptContent(pass, this, name, type, created)
}

fun Source.decrypt(pass: Password256, meta: ContentMeta): Content {
	return EncryptContent(pass, this, meta)
}

open class EncryptSource(val pass: Password256, val source: Source) : Source {
	override fun createInputStream(): InputStream {
		val seq = EncryptedSequence.createSequence(pass)
		return EncryptedContinuousInputStream(seq, source.createInputStream())
	}

	override val length: Long
		get() = source.length
}

class EncryptContent(
	pass: Password256,
	source: Source,
	override val path: String,
	override val ext: String,
	override val lastModified: Long
) : Content, EncryptSource(pass, source) {
	constructor(pass: Password256, source: Source, meta: ContentMeta) : this(
		pass,
		source,
		meta.name,
		meta.type,
		meta.created
	)
}
