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
		const val MAX_WIRE_BLOCK_SIZE = MAXBLOCKSIZE + 256

		/** Internal sentinel ID used in TCPNode.outDeque to signal write thread shutdown. */
		const val CLOSE_SENTINEL_ID = -1L

		/** Client -> Bridge: route wrapped block to target PeerKey (normal priority). */
		const val BRIDGE_TO_PEER_ID = -2L

		/** Client -> Bridge: route wrapped block to target PeerKey (high priority). */
		const val BRIDGE_TO_PEER_PRIORITY_ID = -3L

		/** Client -> Bridge: notify target PeerKey that virtual bridge connection closed. */
		const val BRIDGE_TO_PEER_CLOSE_ID = -4L

		/** Bridge -> Client: deliver wrapped block from source PeerKey (normal priority). */
		const val BRIDGE_FROM_PEER_ID = -5L

		/** Bridge -> Client: deliver wrapped block from source PeerKey (high priority). */
		const val BRIDGE_FROM_PEER_PRIORITY_ID = -6L

		/** Bridge -> Client: notify that virtual bridge connection with source PeerKey closed. */
		const val BRIDGE_FROM_PEER_CLOSE_ID = -7L

		fun read(dis: DataInputStream): TCPBlock {
			val id = dis.readLong()
			val arr = BlockWritable.read(dis, MAX_WIRE_BLOCK_SIZE).arr
			val last = dis.readBoolean()
			return TCPBlock(id, arr, last)
		}
	}

	override fun toString(): String {
		return "TCPBlock[id=$id, length=${data.size}, last=$last]"
	}
}