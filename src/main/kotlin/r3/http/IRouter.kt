package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response

interface IRouter {
	fun findRoute(session: IHTTPSession): Response?
}