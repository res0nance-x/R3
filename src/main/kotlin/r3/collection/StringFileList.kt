package r3.collection

import java.io.*

class StringFileList(val file: File) : MutableSimpleList<String>, Closeable {
	private val os: OutputStream = FileOutputStream(file, true).buffered()
	override fun add(v: String) {
		os.write(v.toByteArray())
		os.write('\n'.code)
	}

	override fun visit(visitor: (String) -> Unit) {
		BufferedReader(InputStreamReader(file.inputStream())).use { r ->
			var line = r.readLine()
			while (line != null) {
				visitor(line)
				line = r.readLine()
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
		fun writeList(file: File, list: List<String>) {
			StringFileList(file).use { sfl ->
				for (x in list) {
					sfl.add(x)
				}
			}
		}

		fun readList(file: File): ArrayList<String> {
			val list = ArrayList<String>()
			StringFileList(file).use { sfl ->
				sfl.visit {
					list.add(it)
				}
			}
			return list
		}
	}
}