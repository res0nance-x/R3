package r3.io

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

interface Writable {
	fun write(dos: DataOutputStream)
}

fun Writable.serialize(): ByteArray {
	val os = ByteArrayOutputStream()
	this.write(DataOutputStream(os))
	os.close()
	return os.toByteArray()
}

class GenericWritable<T>(val obj: T, private val write: (DataOutputStream, value: T) -> Unit) : Writable {
	override fun write(dos: DataOutputStream) {
		write(dos, obj)
	}
}