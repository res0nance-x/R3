package r3.collection

import r3.org.json.JSONObject
import java.io.*

class JsonFileList(val file: File) : MutableSimpleList<JSONObject>, Closeable {
	private val os: OutputStream = FileOutputStream(file, true).buffered()
	override fun add(v: JSONObject) {
		os.write(v.toString().toByteArray())
		os.write('\n'.code)
	}

	override fun visit(visitor: (JSONObject) -> Unit) {
		BufferedReader(InputStreamReader(file.inputStream())).use { r ->
			var line = r.readLine()
			while (line != null) {
				visitor(JSONObject(line))
				line = r.readLine()
			}
		}
	}

	fun flush() {
		os.flush()
	}

	override fun close() {
		os.close()
	}

	companion object {
		fun writeList(file: File, list: List<JSONObject>) {
			JsonFileList(file).use { jfl ->
				for (x in list) {
					jfl.add(x)
				}
			}
		}

		fun readList(file: File): ArrayList<JSONObject> {
			val list = ArrayList<JSONObject>()
			JsonFileList(file).use { jfl ->
				jfl.visit {
					list.add(it)
				}
			}
			return list
		}
	}
}