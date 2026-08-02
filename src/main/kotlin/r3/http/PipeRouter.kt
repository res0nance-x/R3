package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response

class PipeRouter(val router: List<IRouter>) : IRouter {
	override fun findRoute(session: IHTTPSession): Response? {
		for (x in router) {
			val r = x.findRoute(session)
			if (r != null) {
				return r
			}
		}
		return null
	}
}