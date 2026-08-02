package r3.net.tcp

import r3.io.Writable
import r3.net.compare
import r3.source.BlockWritable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress

class InetSocketAddressWritable(val addr: InetSocketAddress) : Comparable<InetSocketAddressWritable>, Writable {
	override fun compareTo(other: InetSocketAddressWritable): Int {
		return compare(addr, other.addr)
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (javaClass != other?.javaClass) {
			return false
		}
		other as InetSocketAddressWritable
		return compare(addr, other.addr) == 0
	}

	override fun hashCode(): Int {
		return addr.address.address.contentHashCode() xor addr.port.hashCode()
	}

	override fun toString(): String {
		return addr.toString()
	}

	override fun write(dos: DataOutputStream) {
		BlockWritable(addr.address.address).write(dos)
		dos.writeInt(addr.port)
	}

	companion object {
		fun read(dis: DataInputStream): InetSocketAddressWritable {
			return InetSocketAddressWritable(
				InetSocketAddress(
					InetAddress.getByAddress(
						BlockWritable.read(dis).arr
					),
					dis.readInt()
				)
			)
		}
	}
}