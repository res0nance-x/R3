package r3.net.discover

import r3.io.serialize
import java.io.Closeable

// TODO
open class WebDiscover() : Closeable {
	val urlList = listOf("fd00::8:8", "graffiti.local", "r3sonance.org")
	fun send(info: PeerAddressInfo) {
		val arr = info.serialize()
		if (arr.size > 1024) {
			throw Exception("Error 4LvHHrxit: Data is too large")
		}
	}

	override fun close() {
	}
}