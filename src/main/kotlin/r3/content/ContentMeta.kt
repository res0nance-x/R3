package r3.content

import r3.io.Writable
import r3.source.StringWritable
import r3.util.dateTime
import r3.util.humanReadableSize
import java.io.DataInputStream
import java.io.DataOutputStream

data class ContentMeta(
	val name: String,
	val type: String,
	val length: Long,
	val created: Long
) : Writable {
	constructor(content: Content) : this(
		content.path,
		content.ext,
		content.length,
		content.lastModified,
	)

	override fun write(dos: DataOutputStream) {
		StringWritable(name).write(dos)
		StringWritable(type).write(dos)
		dos.writeLong(length)
		dos.writeLong(created)
	}

	override fun toString(): String {
		return "Name:$name Type:$type Length:${length.humanReadableSize()} Created:${created.dateTime()}"
	}

	companion object {
		fun read(dis: DataInputStream): ContentMeta {
			return ContentMeta(
				StringWritable.read(dis).str,
				StringWritable.read(dis).str,
				dis.readLong(),
				dis.readLong()
			)
		}
	}
}