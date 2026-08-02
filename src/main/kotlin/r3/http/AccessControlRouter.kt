package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class AccessControlRouter(
	private val basePath: String,
	private val login: (username: String, password: String) -> Boolean
) : IRouter {

	companion object {
		private const val TOKEN_LENGTH = 32
		private val TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
		private val secureRandom = SecureRandom()
	}

	// Map of bearer tokens to username
	private val validTokens = ConcurrentHashMap<String, String>()

	/**
	 * Generate a cryptographically secure random bearer token
	 */
	private fun generateToken(): String {
		return (1..TOKEN_LENGTH)
			.map { TOKEN_CHARS[secureRandom.nextInt(TOKEN_CHARS.length)] }
			.joinToString("")
	}

	/**
	 * Extract bearer token from Authorization header
	 */
	private fun extractBearerToken(session: IHTTPSession): String? {
		val authHeader = session.headers["authorization"] ?: return null
		if (!authHeader.startsWith("Bearer ", ignoreCase = true)) {
			return null
		}
		return authHeader.substring(7).trim()
	}

	/**
	 * Create an unauthorized response
	 */
	private fun unauthorizedResponse(message: String = "Unauthorized"): Response {
		val response = Response.newFixedLengthResponse(
			Status.UNAUTHORIZED,
			"text/plain",
			message
		)
		response.addHeader("WWW-Authenticate", "Bearer realm=\"Relay\"")
		return response
	}

	/**
	 * Create a bad request response
	 */
	private fun badRequestResponse(message: String): Response {
		return Response.newFixedLengthResponse(
			Status.BAD_REQUEST,
			"text/plain",
			"Bad Request: $message"
		)
	}

	override fun findRoute(session: IHTTPSession): Response? {
		if (!session.uri.startsWith(basePath)) {
			return null
		}

		val subPath = session.uri.substring(basePath.length)

		// Handle login endpoint
		if (subPath == "login") {
			if (session.method.name != "POST") {
				return Response.newFixedLengthResponse(
					Status.METHOD_NOT_ALLOWED,
					"text/plain",
					"Method Not Allowed"
				)
			}

			// Parse credentials from request parameters or body
			val username = session.parameters["username"]?.firstOrNull()?:return badRequestResponse("Missing username")
			val password = session.parameters["password"]?.firstOrNull()?:return badRequestResponse("Missing password")

			// Validate credentials
			if (!login(username, password)) {
				return unauthorizedResponse("Invalid credentials")
			}

			// Generate and store token
			val token = generateToken()
			validTokens[token] = username

			// Return token as JSON
			val jsonResponse = """{"token":"$token","username":"$username"}"""
			return Response.newFixedLengthResponse(
				Status.OK,
				"application/json",
				jsonResponse
			)
		}

		// For all other requests, check bearer token
		val token = extractBearerToken(session) ?: return unauthorizedResponse("Missing or invalid Authorization header")

		if (!validTokens.containsKey(token)) {
			return unauthorizedResponse("Invalid or expired token")
		}

		// Token is valid, pass through to next router
		return null
	}

	/**
	 * Revoke a bearer token (for logout functionality)
	 */
	fun revokeToken(token: String): Boolean {
		return validTokens.remove(token) != null
	}

	/**
	 * Get the username associated with a token
	 */
	fun getUsername(token: String): String? {
		return validTokens[token]
	}

	/**
	 * Clear all tokens (for testing or admin purposes)
	 */
	fun clearAllTokens() {
		validTokens.clear()
	}
}