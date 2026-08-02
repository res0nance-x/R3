package r3.source

import r3.io.Writable
import java.io.DataInputStream
import java.io.DataOutputStream

class ListWritable<T : Writable>(val list: List<T>) : Writable {
	override fun write(dos: DataOutputStream) {
		dos.writeInt(list.size)
		for (x in list) {
			x.write(dos)
		}
	}

	companion object {
		fun <T : Writable> read(dis: DataInputStream, read: (DataInputStream) -> T): ListWritable<T> {
			val len = dis.readInt()
			val list = ArrayList<T>()
			repeat(len) {
				list.add(read(dis))
			}
			return ListWritable(list)
		}
	}
}
