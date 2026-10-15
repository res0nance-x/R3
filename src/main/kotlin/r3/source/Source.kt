package r3.source

import r3.hash.hash256
import r3.key.Key256
import java.io.InputStream
import java.io.OutputStream

interface Source {
	fun createInputStream(): InputStream
	val length: Long
}

fun Source.hashKey(): Key256 {
	return createInputStream().use { Key256(it.hash256()) }
}

fun Source.copyTo(sink: Sink) {
	sink.createOutputStream().buffered(r3.io.DEFAULT_COPY_BUFFER_SIZE).use { ostream ->
		this.createInputStream().buffered(r3.io.DEFAULT_COPY_BUFFER_SIZE).use { istream ->
			istream.copyTo(ostream, r3.io.DEFAULT_COPY_BUFFER_SIZE)
		}
	}
}

fun Source.copyTo(ostream: OutputStream) {
	this.createInputStream().buffered(r3.io.DEFAULT_COPY_BUFFER_SIZE).use { istream ->
		istream.copyTo(ostream, r3.io.DEFAULT_COPY_BUFFER_SIZE)
	}
}

fun Source.readBinary(): ByteArray {
	return this.createInputStream().use { it.readAllBytes() }
}

fun Source.readString(): String {
	return this.createInputStream().use { String(it.readAllBytes()) }
}