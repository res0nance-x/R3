package r3.net.tcp

import r3.io.log
import r3.key.Key256
import r3.net.discover.PeerAddressInfo
import r3.net.getAddressListInternal
import r3.pke.ServerKey
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class TCPServer(
	val blockHandler: BlockHandler,
	val nodeList: MutableList<TCPNode> = mutableListOf(),
	val address: InetSocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
	val onAccept: ((TCPNode) -> Unit)? = null,
	val serverKey: ServerKey = ServerKey(Key256.randomKey())
) : Closeable {
	val socketServer = ServerSocket().apply {
		reuseAddress = true
		bind(address, 50)
	}
	val peerAddressInfo: PeerAddressInfo = if (address.address.isAnyLocalAddress) {
		val addressList = getAddressListInternal()
		PeerAddressInfo(serverKey, addressList, socketServer.localPort)
	} else {
		PeerAddressInfo(serverKey, listOf(address.address as InetAddress), socketServer.localPort)
	}

	fun start(daemon: Boolean = true) {
		// socket connection listen loop
		thread(isDaemon = daemon, name = "TCPServer") {
			while (!Thread.interrupted() && !socketServer.isClosed) {
				try {
					val sock = socketServer.accept()
					log("TCPServer: Received connection from ${sock.remoteSocketAddress}")
					handle(sock)
				} catch (_: Exception) {
					log("TCPServer has stopped")
				}
			}
		}
	}

	private fun handle(sock: Socket) {
		val node = TCPNode(sock, blockHandler)
		nodeList.add(node)
		onAccept?.invoke(node)
	}

	override fun close() {
		socketServer.close()
		for (node in nodeList) {
			try {
				node.close()
			} catch (_: Exception) {
			}
		}
	}
}
