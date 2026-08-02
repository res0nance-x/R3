package org.nanohttpd.protocols.http

import org.nanohttpd.protocols.websocket.NanoWSD
import java.io.IOException
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread

class WebSocketManager {
	class ASyncSend(val socket: NanoWSD.WebSocket) {
		private val deque = LinkedBlockingDeque<ByteArray>(10000)
		private var active = true
		val sendThread = thread(isDaemon = true) {
			try {
				while (active) {
					val data = deque.take()
					socket.send(data)
				}
			} catch (e: Exception) {
				println("Stopping send deque thread")
			}
		}

		fun asyncSend(data: ByteArray) {
			if (!deque.offer(data)) {
				// We can't keep up for this client
				println("Client can't keep up.")
				socket.close(NanoWSD.WebSocketFrame.CloseCode.InternalServerError, "Client can't keep up", true)
			}
		}

		fun stopThread() {
			active = false
			sendThread.interrupt()
		}
	}

	val connectedSockets: MutableMap<NanoWSD.WebSocket, ASyncSend> =
		Collections.synchronizedMap(HashMap<NanoWSD.WebSocket, ASyncSend>())
	private val webSocketBuilder = { handler: IHTTPSession ->
		val socket = object : NanoWSD.WebSocket(handler) {
			override fun onOpen() {
				connectedSockets[this] = ASyncSend(this)
			}

			override fun onClose(
				code: NanoWSD.WebSocketFrame.CloseCode?,
				reason: String?,
				initiatedByRemote: Boolean
			) {
				println("WebSocket has closed: code: $code reason: $reason initiatedByRemote: $initiatedByRemote")
				// Remove resources associated with this socket
				connectedSockets[this]?.stopThread()
				connectedSockets.remove(this)
			}

			override fun onMessage(message: NanoWSD.WebSocketFrame) {
				val data = message.binaryPayload
				val sockets = synchronized(connectedSockets) { connectedSockets.values.toList() }
				println("Got sockets ${sockets.size}")
				for (socket in sockets) {
					println(socket.socket)
					if (socket.socket != this) {
						println("Sending to ${socket.socket}")
						socket.asyncSend(data)
					}
				}
			}

			override fun onPong(pong: NanoWSD.WebSocketFrame?) {
				println("Got Pong from client")
			}

			override fun onException(exception: IOException) {
				exception.printStackTrace()
			}
		}
		socket
	}
}