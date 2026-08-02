package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status

class SessionRouter(val path: String) : IRouter {
	val map = HashMap<String, String>()
	override fun findRoute(session: IHTTPSession): Response? {
		if (!session.uri.startsWith(path)) {
			return null
		}
		val subPath = session.uri.substring(path.length)
		return when {
			subPath == "get" -> {
				val key = session.parameters.keys.firstOrNull()
				if (key != null) {
					val value = map[key] ?: ""
					Response.newFixedLengthResponse(
						Status.OK,
						"text/plain",
						value
					)
				} else {
					Response.newFixedLengthResponse(
						Status.NOT_FOUND,
						"text/plain",
						"No Key"
					)
				}
			}

			subPath == "set" -> {
				val key = session.parameters.keys.firstOrNull()
				if (key != null) {
					val value = session.parameters[key]?.firstOrNull() ?: ""
					map[key] = value
				}
				Response.newFixedLengthResponse(
					Status.OK,
					"text/plain",
					"Ok"
				)
			}

			else -> null
		}
	}
}