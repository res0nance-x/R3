package r3.collection

import r3.io.serialize
import r3.key.Key256
import java.io.*

class KeyFileList(val file: File) : MutableSimpleList<Key256>, Closeable {
	private val os: OutputStream = FileOutputStream(file, true).buffered()
	override fun add(v: Key256) {
		os.write(v.serialize())
	}

	override fun visit(visitor: (Key256) -> Unit) {
		DataInputStream(file.inputStream().buffered()).use { dis ->
			while (dis.available() > 0) {
				val key = Key256.read(dis)
				visitor(key)
			}
		}
	}

	fun sync() {
		os.flush()
	}

	override fun close() {
		os.close()
	}

	companion object {
		fun writeList(file: File, list: List<Key256>) {
			KeyFileList(file).use { kfl ->
				for (x in list) {
					kfl.add(x)
				}
			}
		}

		fun readList(file: File): ArrayList<Key256> {
			val list = ArrayList<Key256>()
			KeyFileList(file).use { kfl ->
				kfl.visit {
					list.add(it)
				}
			}
			return list
		}
	}
}