package r3.net.tcp

import r3.io.Writable
import r3.key.Key256
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress

internal class KeyAddressWritable(val method: Byte, val key: Key256, val addr: InetSocketAddress) : Writable {
	override fun write(dos: DataOutputStream) {
		dos.write(method.toInt())
		key.write(dos)
		InetSocketAddressWritable(addr).write(dos)
	}

	companion object {
		fun read(dis: DataInputStream): KeyAddressWritable {
			return KeyAddressWritable(dis.read().toByte(), Key256.read(dis), InetSocketAddressWritable.read(dis).addr)
		}
	}
}
