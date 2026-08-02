package r3.source

import r3.io.Writable
import java.io.DataInputStream
import java.io.DataOutputStream

class StringListWritable(val list: List<String>) : Writable {
	override fun write(dos: DataOutputStream) {
		dos.writeInt(list.size)
		for (str in list) {
			StringWritable(str).write(dos)
		}
	}

	companion object {
		fun read(dis: DataInputStream): StringListWritable {
			val len = dis.readInt()
			val list = ArrayList<String>()
			repeat(len) {
				list.add(StringWritable.read(dis).str)
			}
			return StringListWritable(list)
		}
	}
}