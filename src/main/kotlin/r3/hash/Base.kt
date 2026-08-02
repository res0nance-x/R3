package r3.hash

import java.io.ByteArrayOutputStream
import java.util.*

fun ByteArray.toBase85(): String {
	return String(Z85Encode(this))
}

fun ByteArray.toUBase64(): String {
	return String(Base64.getUrlEncoder().encode(this))
}

fun String.fromUBase64(): ByteArray {
	return Base64.getUrlDecoder().decode(this)
}

fun ByteArray.toBase16(): String {
	val sb = StringBuilder()
	for (x in this) {
		sb.append('a' + ((x.toInt() ushr 4) and 0xF))
		sb.append('a' + (x.toInt() and 0xF))
	}
	return sb.toString()
}

fun String.fromBase16(): ByteArray {
	val baos = ByteArrayOutputStream()
	val charr = this.toCharArray()
	for (i in charr.indices step 2) {
		val b = ((charr[i] - 'a') shl 4) or (charr[i + 1] - 'a')
		baos.write(b)
	}
	return baos.toByteArray()
}

fun String.fromBase85(): ByteArray {
	return Z85Decode(this.toCharArray())
}
