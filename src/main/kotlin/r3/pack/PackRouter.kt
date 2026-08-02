package r3.pack

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import r3.http.IRouter
import r3.http.rangeRequestResponse

class PackRouter(val pack: Pack) : IRouter {
	override fun findRoute(session: IHTTPSession): Response? {
		val content = pack[session.uri.substring(1)] ?: return null
		return rangeRequestResponse(session, content)
	}
}