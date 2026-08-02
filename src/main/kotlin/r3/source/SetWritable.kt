package r3.source

import r3.io.Writable
import java.io.DataInputStream
import java.io.DataOutputStream

class SetWritable<T : Writable>(val set: Set<T>) : Writable {
	override fun write(dos: DataOutputStream) {
		dos.writeInt(set.size)
		for (x in set) {
			x.write(dos)
		}
	}

	companion object {
		fun <T : Writable> read(dis: DataInputStream, read: (DataInputStream) -> T): SetWritable<T> {
			val len = dis.readInt()
			val set = HashSet<T>()
			repeat(len) {
				set.add(read(dis))
			}
			return SetWritable(set)
		}
	}
}
