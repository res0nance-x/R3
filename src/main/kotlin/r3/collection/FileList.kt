package r3.collection

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

class FileList<K>(
	val file: File,
	private val writeValue: (DataOutputStream, key: K) -> Unit,
	private val readValue: (DataInputStream) -> K
) : MutableSimpleList<K> {
	private val dos: DataOutputStream = DataOutputStream(file.outputStream().buffered())
	override fun add(v: K) {
		writeValue(dos, v)
	}

	override fun visit(visitor: (K) -> Unit) {
		DataInputStream(file.inputStream().buffered()).use { dis ->
			while (dis.available() > 0) {
				val value = readValue(dis)
				visitor(value)
			}
		}
	}
}