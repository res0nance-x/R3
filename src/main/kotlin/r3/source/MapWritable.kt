package r3.source

import r3.io.Writable
import java.io.DataInputStream
import java.io.DataOutputStream

class MapWritable(val map: Map<String, String>) : Writable {
	override fun write(dos: DataOutputStream) {
		dos.writeInt(map.size)
		for (me in map) {
			StringWritable(me.key).write(dos)
			StringWritable(me.value).write(dos)
		}
	}

	companion object {
		fun read(dis: DataInputStream): MapWritable {
			val map = LinkedHashMap<String, String>()
			val size = dis.readInt()
			repeat(size) {
				val key = StringWritable.read(dis).str
				val value = StringWritable.read(dis).str
				map[key] = value
			}
			return MapWritable(map)
		}
	}
}