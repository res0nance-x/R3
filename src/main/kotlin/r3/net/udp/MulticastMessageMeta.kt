package r3.net.udp

import r3.io.log
import java.nio.ByteBuffer

class MulticastMessageMeta(val arr: ByteArray) {
	private val buf = ByteBuffer.wrap(arr)
	val type: MulticastMessageType
		get() = try {
			MulticastMessageType.values()[buf.getInt(0)]
		} catch (e: Exception) {
			log("lHMIlzVk: $e")
			MulticastMessageType.UNKNOWN
		}

	override fun toString(): String {
		return "$type $arr.size"
	}
}