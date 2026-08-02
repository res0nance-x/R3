package r3.hash

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

fun hash(md: MessageDigest, inStream: InputStream): ByteArray {
	val buf = ByteArray(8192)
	var read = inStream.read(buf)
	while (read > -1) {
		md.update(buf, 0, read)
		read = inStream.read(buf)
	}
	return md.digest()
}

fun File.hash256(): ByteArray {
	FileInputStream(this).use {
		return it.hash256()
	}
}

fun InputStream.lengthAndHash256(): Pair<Long, ByteArray> {
	val md = MessageDigest.getInstance("SHA-256")
	val buf = ByteArray(8192)
	var length = 0L
	var read = read(buf)
	while (read > -1) {
		length += read
		md.update(buf, 0, read)
		read = read(buf)
	}
	return Pair(length, md.digest())
}

fun InputStream.hash256(): ByteArray {
	val md = MessageDigest.getInstance("SHA-256")
	val buf = ByteArray(8192)
	var read = read(buf)
	while (read > -1) {
		md.update(buf, 0, read)
		read = read(buf)
	}
	return md.digest()
}

fun InputStream.hash128(): ByteArray {
	val md = MessageDigest.getInstance("SHA-256")
	val buf = ByteArray(8192)
	var read = read(buf)
	while (read > -1) {
		md.update(buf, 0, read)
		read = read(buf)
	}
	return md.digest().sliceArray(0..15)
}

fun ByteArray.hash128(): ByteArray {
	val md = MessageDigest.getInstance("SHA-256")
	return md.digest(this).sliceArray(0..15)
}

fun ByteArray.hash256(): ByteArray {
	val md = MessageDigest.getInstance("SHA-256")
	return md.digest(this)
}

fun ByteArray.hash512(): ByteArray {
	val md = MessageDigest.getInstance("SHA-512")
	return md.digest(this)
}

fun String.hash64(): String {
	return this.toByteArray().hash64().toString(36)
}

fun ByteArray.hash64(): ULong {
	val FNV_PRIME = 0x100000001b3UL
	var hash = 0xcbf29ce484222325UL
	for (b in this) {
		hash = hash xor (b.toUByte().toULong())
		hash = (hash * FNV_PRIME) and 0xFFFFFFFFFFFFFFFFUL
	}
	return hash
}