package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status
import r3.io.readAll

class ResourceRouter(val classLoader: ClassLoader, val base: String) : IRouter {
	override fun findRoute(session: IHTTPSession): Response? {
		val path = base + session.uri
		val data = classLoader.getResourceAsStream(path)?.readAll() ?: return null
		val response = Response.newFixedLengthResponse(Status.OK, MimeMap.getMimeType(path), data)
		return response
	}
}