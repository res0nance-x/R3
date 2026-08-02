package r3.net.discover

import r3.io.debug
import r3.io.log
import r3.io.toDataInputStream
import r3.net.createIP6DatagramSocket
import r3.net.usableNetworkInterfaceList
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class MulticastDiscover {
	val ip6FixedMulticastAddress =
		InetSocketAddress(InetAddress.getByName("ff32:8395:4ab6:e403:8f2a:b92f:beaa:80da"), 45228)

	fun discover(found: (PeerAddressInfo) -> Unit) {
		val threads = mutableListOf<Thread>()
		for (inf in usableNetworkInterfaceList()) {
			if (inf.supportsMulticast() && !inf.isLoopback && inf.isUp && !inf.isVirtual) {
				thread(isDaemon = true) {
					var sock: DatagramSocket? = null
					try {
						sock = inf.createIP6DatagramSocket().apply {
							soTimeout = 1000
						}
						sock.send(DatagramPacket(ByteArray(0), 0, ip6FixedMulticastAddress))
						val buffer = ByteArray(1500)
						while (true) {
							val datagram = DatagramPacket(buffer, buffer.size)
							try {
								sock.receive(datagram)
								val info = PeerAddressInfo.read(datagram.data.toDataInputStream())
								val updatedList = (info.addrList + datagram.address)
									.filter { !it.isLinkLocalAddress }
									.distinct()
								if (updatedList.isNotEmpty()) {
									found(PeerAddressInfo(info.serverKey, updatedList, info.port, info.isRelay))
								}
							} catch (e: java.net.SocketTimeoutException) {
								break
							}
						}
					} catch (e: Exception) {
						debug("MulticastDiscover: Error on $inf: $e")
					} finally {
						try {
							sock?.close()
						} catch (_: Exception) {
						}
					}
				}.also { threads.add(it) }
			}
		}
		for (t in threads) {
			try {
				t.join(1100)
			} catch (_: Exception) {
			}
		}
	}
}