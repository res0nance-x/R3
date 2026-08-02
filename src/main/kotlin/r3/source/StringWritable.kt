package r3.source

import r3.io.Writable
import java.io.DataInputStream
import java.io.DataOutputStream

class StringWritable(val str: String) : Writable {
	override fun write(dos: DataOutputStream) {
		val arr = str.toByteArray()
		dos.writeInt(arr.size)
		dos.write(arr)
	}

	companion object {
		fun read(dis: DataInputStream): StringWritable {
			val len = dis.readInt()
			val arr = ByteArray(len)
			dis.readFully(arr)
			return StringWritable(String(arr))
		}
	}

	override fun toString(): String {
		return str
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (javaClass != other?.javaClass) {
			return false
		}
		other as StringWritable
		return str == other.str
	}

	override fun hashCode(): Int {
		return str.hashCode()
	}
}