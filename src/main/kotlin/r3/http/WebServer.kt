package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.NanoHTTPD
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status
import org.nanohttpd.protocols.websocket.NanoWSD
import org.nanohttpd.protocols.websocket.NanoWSD.WebSocketFrame
import r3.content.BinaryContent
import r3.content.Content
import r3.content.FileContent
import r3.content.JsonContent
import r3.io.BoundedInputStream
import r3.io.DoNotCloseInputStream
import r3.io.createOutputStream
import r3.io.log
import r3.org.json.JSONArray
import r3.org.json.JSONObject
import r3.source.readString
import r3.util.srnd
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.logging.Filter
import java.util.logging.Logger

fun interface ContentHandler {
	fun handle(header: JSONObject, content: Content?): Content?
	fun onResponse(header: JSONObject, response: Response) {}
}

fun toHeader(request: IHTTPSession): JSONObject {
	val json = JSONObject()
	val param = JSONObject()
	for ((key, value) in request.parameters) {
		val v = when (value.size) {
			0 -> ""
			1 -> value[0]
			else -> {
				val arr = JSONArray()
				for (x in value) {
					arr.put(x)
				}
				arr
			}
		}
		json.put(key, v)
		param.put(key, v)
	}
	if (!param.isEmpty) {
		json.put("param", param)
	}
	// Headers override parameters of the same name
	for ((key, value) in request.headers) {
		json.put(key, value)
	}
	for (cookieName in request.cookies) {
		json.put(cookieName, request.cookies.read(cookieName))
	}
	// core keys override parameters and headers
	json.put("path", java.net.URLDecoder.decode(request.uri, "UTF-8"))
	json.put("remote-ip", request.remoteInetSocketAddress.let { "${it.address}:${it.port}" })
	json.put("method", request.method.toString())
	return json
}

fun extractFilename(contentDisposition: String?): String? {
	if (contentDisposition == null) return null
	// Regex to match filename="value" or filename=value (case-insensitive)
	val regex = """filename\s*=\s*"?([^"\s;]+)"?""".toRegex(RegexOption.IGNORE_CASE)
	val matchResult = regex.find(contentDisposition)

	return matchResult?.groups?.get(1)?.value
}

fun getRandomName(): String {
	val arr = srnd.getByteArray(16)
	return arr.toHexString()
}

class ContentWebSocketHandler(
	handshake: IHTTPSession,
	val webSocketList: MutableList<NanoWSD.WebSocket>,
	val handlers: List<ContentHandler>
) : NanoWSD.WebSocket(handshake) {
	override fun onOpen() {
		webSocketList.add(this)
	}

	override fun onClose(
		code: WebSocketFrame.CloseCode,
		reason: String?,
		initiatedByRemote: Boolean
	) {
		webSocketList.remove(this)
	}

	override fun onException(exception: IOException?) {
		log("WebSocket exception $exception")
		webSocketList.remove(this)
	}

	override fun onMessage(frame: WebSocketFrame) {
		try {
			val header = toHeader(this.handshakeRequest)
			header.put("type", "websocket")
			if (!frame.isFin) {
				log("WebSocket: We don't handle partial messages")
				return
			}
			// Content is always expected to be JSON
			if (frame.opCode == WebSocketFrame.OpCode.Binary) {
				log("WebSocket: We don't handle binary content")
				return
			}
			val content = JsonContent(frame.textPayload)
			for (h in handlers) {
				val r = h.handle(header, content)
				if (r != null) {
					if (r.ext == "json") {
						this.send(r.readString())
						return
					} else {
						log("WebSocket: We only handle JSON content")
					}
				}
			}
		} catch (e: Exception) {
			log("WebSocket: onMessage error - $e")
		}
	}

	override fun onPong(pong: WebSocketFrame) {}
}

class WebServer(
	host: String?,
	port: Int,
	val tmpFileDir: File
) : NanoWSD(host, port) {
	private val SIZE_THRESHOLD = 32768
	val handlers = ArrayList<ContentHandler>()
	val webSocketList: MutableList<WebSocket> = Collections.synchronizedList(mutableListOf<WebSocket>())
	fun sendToAllWebSockets(message: String) {
		val iter = webSocketList.iterator()
		while (iter.hasNext()) {
			val socket = iter.next()
			if (socket.isOpen) {
				try {
					socket.send(message)
				} catch (e: IOException) {
					log("Failed to send message to WebSocket: $e")
				}
			} else {
				log("WebSocket is closed, removing from list")
				iter.remove()
			}
		}
	}

	override fun openWebSocket(handshake: IHTTPSession): WebSocket {
		return ContentWebSocketHandler(handshake, webSocketList, handlers)
	}

	companion object {
		init {
			Logger.getLogger(NanoHTTPD::class.java.name).filter = Filter {
				it.message != "Could not send response to the client"
			}
		}
	}

	override fun serveHttp(session: IHTTPSession): Response {
		val contentLength = session.headers["content-length"]?.toLong() ?: 0L
		val contentType = session.headers["content-type"] ?: ".bin"
		val ext = MimeMap.getExtensionForMimeType(contentType)
		val name = getRandomName() + "." + ext
		val content = if (contentLength > 0) {
			val istream = DoNotCloseInputStream(
				BoundedInputStream(
					session.inputStream,
					contentLength
				)
			)
			if (contentLength < SIZE_THRESHOLD) {
				val arr = istream.readAllBytes()
				BinaryContent(arr, session.uri, ext)
			} else {
				val tmpFile = File(tmpFileDir, name)
				tmpFile.createOutputStream().use {
					istream.copyTo(it)
				}
				FileContent(tmpFile)
			}
		} else {
			null
		}
		val header = toHeader(session)
		try {
			for (h in handlers) {
				val responseContent = try {
					h.handle(header, content)
				} catch (e: Exception) {
					log("handler exception - $e")
					null
				}
				if (responseContent != null) {
					val response = rangeRequestResponse(session, responseContent)
					h.onResponse(header, response)
					return response
				}
			}
		} catch (e: Exception) {
			log("Exception processing request: ${session.uri} - $e")
		} finally {
			if (content is FileContent) {
				content.delete()
			}
		}
		return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found")
	}
}