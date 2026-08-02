package r3.net.discover

import r3.io.log
import r3.io.serialize
import r3.net.createIP4DatagramSocket
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.NetworkInterface
import java.net.SocketException
import kotlin.concurrent.thread

class ScanDiscoverServer(info: PeerAddressInfo) : Closeable {
	class InfSock(val inf: NetworkInterface, val sock: DatagramSocket) {
		override fun toString(): String {
			return "InfSock: inf=${inf.name}, sock=${sock.localSocketAddress}"
		}
	}

	private val data = info.serialize()
	private val scanPort = 52852
	private val receiveList = ArrayList<InfSock>()
	fun start(daemon: Boolean = true): List<Thread> {
		val threads = mutableListOf<Thread>()
		for (inf in NetworkInterface.getNetworkInterfaces()) {
			if (!inf.isLoopback
				&& inf.isUp
				&& !inf.isVirtual
				&& inf.inetAddresses.toList().isNotEmpty()
			) {
				try {
					val sock = inf.createIP4DatagramSocket(scanPort)
					receiveList.add(InfSock(inf, sock))
					threads.add(thread(isDaemon = daemon, name = "ScanDiscoverIP4Server-${inf.name}") {
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
									log("ScanDiscoverIP4Server: Error receiving packet: ${e.message}")
								}
								break
							}
						}
					})
				} catch (e: Exception) {
					log("ScanDiscoverIP4Server: $e on $inf")
				}
			}
		}
		return threads
	}

	override fun close() {
		for (receiver in receiveList) {
			try {
				receiver.sock.close()
			} catch (e: Exception) {
				log("ScanDiscoverIP4Server: $e")
			}
		}
	}
}