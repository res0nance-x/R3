package r3.http

import org.nanohttpd.protocols.http.response.Response
import r3.content.BinaryContent
import r3.content.Content
import r3.key.Key128
import r3.org.json.JSONObject

class AuthorizationHandler(
	initialStartupKey: Key128 = Key128.randomKey(),
	val authSecret: Key128 = Key128.randomKey(),
	val cookieName: String = "graffiti_token",
	val redirectTo: String = "/index.html"
) : ContentHandler {

	@Volatile
	var startupKey: Key128 = initialStartupKey

	fun rotateStartupKey(): Key128 {
		val newKey = Key128.randomKey()
		startupKey = newKey
		return newKey
	}

	private val initHtmlBytes = """
		<!DOCTYPE html>
		<html>
		  <head>
		    <meta charset="utf-8">
		    <title>Graffiti</title>
		    <script>window.location.replace('$redirectTo');</script>
		    <meta http-equiv="refresh" content="0; url=$redirectTo">
		  </head>
		  <body></body>
		</html>
	""".trimIndent().toByteArray(Charsets.UTF_8)

	override fun handle(header: JSONObject, content: Content?): Content? {
		val path = header.optString("path", "")
		val currentStartupKeyStr = startupKey.toString()
		val currentStartupKeyHex = startupKey.arr.toHexString()

		// Match startup path: either "/<startupKey>" or "/auth/<startupKey>" (supports hex or base64)
		if (path == "/$currentStartupKeyStr" || path == "/auth/$currentStartupKeyStr" ||
			path == "/$currentStartupKeyHex" || path == "/auth/$currentStartupKeyHex"
		) {
			return BinaryContent(initHtmlBytes, "init.html", "html")
		}

		// Validate incoming authorization credentials
		val cookieVal = header.optString(cookieName, "").trim()
		val authHeader = header.optString("authorization", "").trim()
		val bearerToken = if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
			authHeader.substring(7).trim()
		} else ""
		val xAuthToken = header.optString("x-auth-token", "").trim()

		val candidateToken = when {
			cookieVal.isNotEmpty() -> cookieVal
			bearerToken.isNotEmpty() -> bearerToken
			xAuthToken.isNotEmpty() -> xAuthToken
			else -> ""
		}

		val secretHex = authSecret.arr.toHexString()
		val secretBase64 = authSecret.toString()
		if (candidateToken == secretHex || candidateToken == secretBase64) {
			return null // Authorized: pass through to next handler in pipeline
		}

		throw UnauthorizedException("Unauthorized: missing or invalid credentials")
	}

	override fun onResponse(header: JSONObject, response: Response) {
		val path = header.optString("path", "")
		val currentStartupKeyStr = startupKey.toString()
		val currentStartupKeyHex = startupKey.arr.toHexString()
		if (path == "/$currentStartupKeyStr" || path == "/auth/$currentStartupKeyStr" ||
			path == "/$currentStartupKeyHex" || path == "/auth/$currentStartupKeyHex"
		) {
			val secretHex = authSecret.arr.toHexString()
			response.addHeader(
				"Set-Cookie",
				"$cookieName=$secretHex; Path=/; SameSite=Strict; HttpOnly"
			)
			// Rotate startup key on use so the URL cannot be reused
			rotateStartupKey()
		}
	}
}
