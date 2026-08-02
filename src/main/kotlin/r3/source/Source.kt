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
	sink.createOutputStream().use { ostream ->
		this.createInputStream().use { istream ->
			istream.copyTo(ostream)
		}
	}
}

fun Source.copyTo(ostream: OutputStream) {
	this.createInputStream().use { istream ->
		istream.copyTo(ostream)
	}
}

fun Source.readBinary(): ByteArray {
	return this.createInputStream().use { it.readAllBytes() }
}

fun Source.readString(): String {
	return this.createInputStream().use { String(it.readAllBytes()) }
}