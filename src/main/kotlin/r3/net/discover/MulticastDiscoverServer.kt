package r3.net.discover

import r3.io.log
import r3.io.serialize
import r3.net.createMulticastSocket
import r3.net.usableNetworkInterfaceList
import java.io.Closeable
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketException
import kotlin.concurrent.thread

class MulticastDiscoverServer(info: PeerAddressInfo) : Closeable {
	private val listenSockets = mutableListOf<MulticastSocket>()
	private val threads = mutableListOf<Thread>()
	private val data = info.serialize()
	val ip6FixedMulticastAddress =
		InetSocketAddress(InetAddress.getByName("ff32:8395:4ab6:e403:8f2a:b92f:beaa:80da"), 45228)

	fun start(daemon: Boolean = true): List<Thread> {
		val threads = mutableListOf<Thread>()
		for (inf in usableNetworkInterfaceList()) {
			try {
				if (inf.supportsMulticast() && !inf.isLoopback && inf.isUp && !inf.isVirtual) {
					try {
						val sock = createMulticastSocket(ip6FixedMulticastAddress, inf)
						listenSockets.add(sock)
						val thread = thread(isDaemon = daemon, name = "MulticastDiscoverServer-$inf") {
							val buffer = ByteArray(1500)
							val packet = DatagramPacket(buffer, buffer.size)

							while (!Thread.interrupted()) {
								try {
									sock.receive(packet)
									sock.send(DatagramPacket(data, data.size, packet.address, packet.port))
								} catch (_: SocketException) {
									break
								} catch (e: Exception) {
									if (!Thread.interrupted()) {
										log("Error receiving packet: ${e.message}")
									}
									break
								}
							}
						}
						threads.add(thread)
					} catch (e: Exception) {
						log("Failed to create multicast socket on $inf: ${e.message}")
					}
				}
			} catch (e: Exception) {
				log("Error creating multicast socket on $inf")
			}
		}
		return threads
	}

	override fun close() {
		threads.forEach { it.interrupt() }
		listenSockets.forEach {
			try {
				it.close()
			} catch (_: Exception) {
			}
		}
	}
}