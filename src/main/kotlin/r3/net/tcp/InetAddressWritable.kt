package r3.net.tcp

import r3.io.Writable
import r3.source.BlockWritable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress

class InetAddressWritable(val addr: InetAddress) : Writable {
	override fun toString(): String {
		return addr.toString()
	}

	override fun write(dos: DataOutputStream) {
		BlockWritable(addr.address).write(dos)
	}

	companion object {
		fun read(dis: DataInputStream): InetAddressWritable {
			return InetAddressWritable(
				InetAddress.getByAddress(
					BlockWritable.read(dis).arr
				),
			)
		}
	}
}