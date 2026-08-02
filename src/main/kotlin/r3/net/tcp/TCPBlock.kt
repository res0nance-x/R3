package r3.net.tcp

import r3.io.Writable
import r3.source.BlockWritable
import java.io.DataInputStream
import java.io.DataOutputStream

open class TCPBlock(val id: Long, val data: ByteArray, val last: Boolean) : Writable {
	override fun write(dos: DataOutputStream) {
		dos.writeLong(id)
		BlockWritable(data).write(dos)
		dos.writeBoolean(last)
	}

	companion object {
		const val MAXBLOCKSIZE = 65536
		fun read(dis: DataInputStream): TCPBlock {
			val id = dis.readLong()
			val arr = BlockWritable.read(dis, MAXBLOCKSIZE).arr
			val last = dis.readBoolean()
			return TCPBlock(id, arr, last)
		}
	}

	override fun toString(): String {
		return "TCPBlock[id=$id, length=${data.size}, last=$last]"
	}
}