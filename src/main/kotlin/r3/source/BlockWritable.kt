package r3.source

import r3.hash.hash256
import r3.hash.toUBase64
import r3.io.Writable
import r3.io.toDataInputStream
import java.io.DataInputStream
import java.io.DataOutputStream

open class BlockWritable(
	val arr: ByteArray
) : Writable {
	fun toDataInputStream(): DataInputStream {
		return arr.toDataInputStream()
	}

	override fun write(dos: DataOutputStream) {
		dos.writeInt(arr.size)
		dos.write(arr)
	}

	companion object {
		fun read(dis: DataInputStream, maxSize: Int = Int.MAX_VALUE): BlockWritable {
			val len = dis.readInt()
			if (len < 0 || len > maxSize) {
				throw Exception("Invalid block size of $len")
			}
			val arr = ByteArray(len)
			dis.readFully(arr)
			return BlockWritable(arr)
		}
	}

	override fun toString(): String {
		return "BlockWritable[${arr.size}]"
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is BlockWritable) {
			return false
		}
		return arr.contentEquals(other.arr)
	}

	override fun hashCode(): Int {
		return arr.contentHashCode()
	}

	fun hash256(): String {
		return arr.hash256().toUBase64()
	}
}