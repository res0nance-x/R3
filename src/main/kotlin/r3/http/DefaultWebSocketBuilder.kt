package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.websocket.NanoWSD
import r3.io.log
import java.io.IOException

val defaultWebSocketBuilder: (session: IHTTPSession) -> NanoWSD.WebSocket = { session: IHTTPSession ->
	object : NanoWSD.WebSocket(session) {
		override fun onOpen() {
			log("WebSocket opened")
		}

		override fun onClose(
			code: NanoWSD.WebSocketFrame.CloseCode?,
			reason: String?,
			initiatedByRemote: Boolean
		) {
			log("WebSocket closed")
		}

		override fun onMessage(message: NanoWSD.WebSocketFrame?) {
			log("WebSocket message $message")
		}

		override fun onPong(pong: NanoWSD.WebSocketFrame?) {
			log("WebSocket pong $pong")
		}

		override fun onException(exception: IOException?) {
			log("WebSocket exception $exception")
		}
	}
}