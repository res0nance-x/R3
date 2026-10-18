package r3.net

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import r3.net.discover.MulticastDiscover
import r3.net.discover.MulticastDiscoverServer
import r3.net.discover.PeerAddressInfo
import r3.pke.ServerKey
import java.net.InetAddress
import java.util.Collections

class MulticastSocketTest {

	@Test
	fun testMulticastDiscoverEndToEnd() {
		val testKey = ServerKey(ByteArray(32) { 42.toByte() })
		val testInfo = PeerAddressInfo(testKey, listOf(InetAddress.getByName("127.0.0.1")), 54321, false)

		val server = MulticastDiscoverServer(testInfo)
		val threads = server.start(daemon = true)
		println("Started ${threads.size} server listener threads")

		val discovered = Collections.synchronizedList(mutableListOf<PeerAddressInfo>())
		try {
			MulticastDiscover().discover { peer ->
				println("Discovered peer: $peer")
				discovered.add(peer)
			}
		} finally {
			server.close()
		}

		assertTrue(discovered.any { it.serverKey == testKey }, "Should have discovered test peer via IPv4 multicast/broadcast")
	}
}
