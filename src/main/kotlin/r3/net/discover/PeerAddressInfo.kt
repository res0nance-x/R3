package r3.net.discover

import r3.io.Writable
import r3.net.tcp.InetAddressWritable
import r3.pke.ServerKey
import r3.pke.name
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress

class PeerAddressInfo(val serverKey: ServerKey, val addrList: List<InetAddress>, val port: Int, val isRelay: Boolean = false) : Writable {
	override fun write(dos: DataOutputStream) {
		serverKey.write(dos)
		dos.writeInt(addrList.size)
		for (addr in addrList) {
			InetAddressWritable(addr).write(dos)
		}
		dos.writeInt(port)
		dos.writeBoolean(isRelay)
	}

	companion object {
		fun read(dis: DataInputStream): PeerAddressInfo {
			val serverKey = ServerKey.read(dis)
			val addrList = List(dis.readInt()) { InetAddressWritable.read(dis).addr }
			val port = dis.readInt()
			val isRelay = try { dis.readBoolean() } catch (e: Exception) { false }
			return PeerAddressInfo(serverKey, addrList, port, isRelay)
		}
	}

	override fun toString(): String {
		return "${serverKey.name} ${addrList.joinToString(", ", "[", "]")}:$port"
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as PeerAddressInfo

		return serverKey == other.serverKey
	}

	override fun hashCode(): Int {
		return serverKey.hashCode()
	}
}
