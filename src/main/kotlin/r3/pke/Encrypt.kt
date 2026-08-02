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
		val ostream = sink.createOutputStream()
		return EncryptedContinuousOutputStream(seq, ostream).use { out ->
			BufferedInputStream(source.createInputStream()).use { istream ->
				istream.copyTo(out)
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
		val istream = source.createInputStream().buffered()
		EncryptedContinuousInputStream(seq, istream).use { eistream ->
			BufferedOutputStream(sink.createOutputStream()).use { ostream ->
				eistream.copyTo(ostream)
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
