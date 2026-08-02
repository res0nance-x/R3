package r3.content

import r3.org.json.JSONArray
import r3.org.json.JSONObject
import r3.source.BinarySource
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.time.Instant

open class BinaryContent(
	arr: ByteArray,
	override val path: String,
	override val ext: String,
	override val lastModified: Long = Instant.now().toEpochMilli()
) :
	Content, BinarySource(arr) {
	override fun createInputStream(): ByteArrayInputStream {
		return ByteArrayInputStream(arr)
	}

	override fun toString(): String {
		return "$path $ext $length"
	}

	companion object {
		fun read(dis: DataInputStream): BinaryContent {
			val header = ContentMeta.read(dis)
			val arr = ByteArray(header.length.toInt())
			dis.readFully(arr)
			return BinaryContent(arr, header.name, header.type)
		}
	}
}

class HTMLContent(html: String) : BinaryContent(html.toByteArray(), "html", "html")
class TextContent(text: String) : BinaryContent(text.toByteArray(), "text", "txt")
class JsonContent(json: String) :
	BinaryContent(json.toByteArray(), "json", "json") {
	constructor(json: JSONObject) : this(json.toString())
	constructor(json: JSONArray) : this(json.toString())
}
