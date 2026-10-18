package r3.net.discover

import r3.io.debug
import r3.io.toDataInputStream
import r3.net.createIP4DatagramSocket
import r3.net.createIP6DatagramSocket
import r3.net.getIP4BroadcastAddress
import r3.net.hasIP4
import r3.net.hasIP6
import r3.net.usableNetworkInterfaceList
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class MulticastDiscover {
	val ip6FixedMulticastAddress =
		InetSocketAddress(InetAddress.getByName("ff32:8395:4ab6:e403:8f2a:b92f:beaa:80da"), 45228)
	val ip4FixedMulticastAddress =
		InetSocketAddress(InetAddress.getByName("239.255.45.228"), 45228)

	fun discover(found: (PeerAddressInfo) -> Unit) {
		val threads = mutableListOf<Thread>()
		for (inf in usableNetworkInterfaceList()) {
			if (inf.supportsMulticast() && !inf.isLoopback && inf.isUp && !inf.isVirtual) {
				if (inf.hasIP4()) {
					thread(isDaemon = true, name = "MulticastDiscover-IP4-$inf") {
						var sock: DatagramSocket? = null
						try {
							sock = inf.createIP4DatagramSocket().apply {
								soTimeout = 1000
							}
							sock.send(DatagramPacket(ByteArray(0), 0, ip4FixedMulticastAddress))
							inf.getIP4BroadcastAddress()?.let { bcast ->
								try {
									sock.send(DatagramPacket(ByteArray(0), 0, InetSocketAddress(bcast, ip4FixedMulticastAddress.port)))
								} catch (_: Exception) {}
							}
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
							debug("MulticastDiscover IP4: Error on $inf: $e")
						} finally {
							try {
								sock?.close()
							} catch (_: Exception) {
							}
						}
					}.also { threads.add(it) }
				}

				if (inf.hasIP6()) {
					thread(isDaemon = true, name = "MulticastDiscover-IP6-$inf") {
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
							debug("MulticastDiscover IP6: Error on $inf: $e")
						} finally {
							try {
								sock?.close()
							} catch (_: Exception) {
							}
						}
					}.also { threads.add(it) }
				}
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