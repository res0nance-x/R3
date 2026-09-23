package r3.http

import org.nanohttpd.protocols.http.response.Response
import r3.content.Content
import r3.org.json.JSONObject
import java.net.URI

class HostOriginHandler(
	val allowedHosts: Set<String> = setOf("localhost", "127.0.0.1", "::1")
) : ContentHandler {

	override fun handle(header: JSONObject, content: Content?): Content? {
		val hostHeader = header.optString("host", "").trim()
		if (hostHeader.isNotEmpty()) {
			val hostName = hostHeader.substringBefore(':').removeSurrounding("[", "]").lowercase()
			if (hostName !in allowedHosts) {
				throw ForbiddenException("Forbidden: Invalid Host header '$hostHeader'")
			}
		}

		val originHeader = header.optString("origin", "").trim()
		if (originHeader.isNotEmpty()) {
			val originHost = try {
				URI(originHeader).host?.removeSurrounding("[", "]")?.lowercase()
			} catch (_: Exception) {
				null
			}
			if (originHost == null || originHost !in allowedHosts) {
				throw ForbiddenException("Forbidden: Invalid Origin header '$originHeader'")
			}
		}

		return null
	}

	override fun onResponse(header: JSONObject, response: Response) {}
}
