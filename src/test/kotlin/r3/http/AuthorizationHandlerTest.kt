package r3.http

import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status
import r3.content.BinaryContent
import r3.content.Content
import r3.key.Key128
import r3.org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthorizationHandlerTest {

	@Test
	fun testAuthAndHostOriginFlow() {
		val tmpDir = File(System.getProperty("java.io.tmpdir"), "test-webserver-" + System.currentTimeMillis())
		tmpDir.mkdirs()

		val startupKey = Key128.randomKey()
		val authHandler = AuthorizationHandler(initialStartupKey = startupKey)
		val hostOriginHandler = HostOriginHandler()

		val dummyHandler = ContentHandler { header, _ ->
			if (header.optString("path") == "/test") {
				BinaryContent("Hello World".toByteArray(Charsets.UTF_8), "test.txt", "txt")
			} else null
		}

		val server = WebServer("localhost", 0, tmpDir)
		server.handlers.add(hostOriginHandler)
		server.handlers.add(authHandler)
		server.handlers.add(dummyHandler)

		server.start(0, false)
		val port = server.listeningPort

		try {
			val initialStartupKeyStr = startupKey.toString()

			// 1. Unauthorized request (no cookie) -> 401
			val unauthConn = URI("http://localhost:$port/test").toURL().openConnection() as HttpURLConnection
			unauthConn.instanceFollowRedirects = false
			assertEquals(401, unauthConn.responseCode)

			// 2. Request with forged Host header -> 403
			val rawHostResponse = java.net.Socket("localhost", port).use { socket ->
				val out = socket.getOutputStream()
				out.write("GET /test HTTP/1.1\r\nHost: evil.com\r\n\r\n".toByteArray(Charsets.UTF_8))
				out.flush()
				socket.getInputStream().bufferedReader().readLine()
			}
			assertTrue(rawHostResponse.contains("403"))

			// 3. Request with forged Origin header -> 403
			val rawOriginResponse = java.net.Socket("localhost", port).use { socket ->
				val out = socket.getOutputStream()
				out.write("GET /test HTTP/1.1\r\nHost: localhost:$port\r\nOrigin: http://evil.com\r\n\r\n".toByteArray(Charsets.UTF_8))
				out.flush()
				socket.getInputStream().bufferedReader().readLine()
			}
			assertTrue(rawOriginResponse.contains("403"))

			// 4. Access startup URL -> 200 OK, returns init.html and Set-Cookie
			val startupConn = URI("http://localhost:$port/$initialStartupKeyStr").toURL().openConnection() as HttpURLConnection
			startupConn.instanceFollowRedirects = false
			assertEquals(200, startupConn.responseCode)
			val body = startupConn.inputStream.bufferedReader().readText()
			assertTrue(body.contains("window.location.replace('/index.html')"))

			val setCookie = startupConn.getHeaderField("Set-Cookie")
			assertNotNull(setCookie)
			assertTrue(setCookie.contains("graffiti_token="))
			assertTrue(setCookie.contains("SameSite=Strict"))
			assertTrue(setCookie.contains("HttpOnly"))

			// Extract cookie value
			val cookieVal = setCookie.substringAfter("graffiti_token=").substringBefore(";")

			// 5. Subsequent request with valid cookie -> 200 OK
			val authConn = URI("http://localhost:$port/test").toURL().openConnection() as HttpURLConnection
			authConn.setRequestProperty("Cookie", "graffiti_token=$cookieVal")
			assertEquals(200, authConn.responseCode)
			val authBody = authConn.inputStream.bufferedReader().readText()
			assertEquals("Hello World", authBody)

			// 6. Request with Authorization: Bearer header -> 200 OK
			val bearerConn = URI("http://localhost:$port/test").toURL().openConnection() as HttpURLConnection
			bearerConn.setRequestProperty("Authorization", "Bearer $cookieVal")
			assertEquals(200, bearerConn.responseCode)

			// 7. Request with wrong cookie -> 401
			val badCookieConn = URI("http://localhost:$port/test").toURL().openConnection() as HttpURLConnection
			badCookieConn.setRequestProperty("Cookie", "graffiti_token=invalid_token")
			assertEquals(401, badCookieConn.responseCode)

			// 8. Re-access old startup URL -> rotated, so not recognized as startup URL, and no cookie -> 401
			val reuseConn = URI("http://localhost:$port/$initialStartupKeyStr").toURL().openConnection() as HttpURLConnection
			assertEquals(401, reuseConn.responseCode)

			// 9. Unauthorized WebSocket handshake -> 401
			val unauthWsResponse = java.net.Socket("localhost", port).use { socket ->
				val out = socket.getOutputStream()
				out.write((
					"GET /api/notify HTTP/1.1\r\n" +
					"Host: localhost:$port\r\n" +
					"Upgrade: websocket\r\n" +
					"Connection: Upgrade\r\n" +
					"Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
					"Sec-WebSocket-Version: 13\r\n\r\n"
				).toByteArray(Charsets.UTF_8))
				out.flush()
				socket.getInputStream().bufferedReader().readLine()
			}
			assertTrue(unauthWsResponse.contains("401"))

			// 10. Authorized WebSocket handshake -> 101 Switching Protocols
			val authWsResponse = java.net.Socket("localhost", port).use { socket ->
				val out = socket.getOutputStream()
				out.write((
					"GET /api/notify HTTP/1.1\r\n" +
					"Host: localhost:$port\r\n" +
					"Upgrade: websocket\r\n" +
					"Connection: Upgrade\r\n" +
					"Cookie: graffiti_token=$cookieVal\r\n" +
					"Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
					"Sec-WebSocket-Version: 13\r\n\r\n"
				).toByteArray(Charsets.UTF_8))
				out.flush()
				socket.getInputStream().bufferedReader().readLine()
			}
			assertTrue(authWsResponse.contains("101"))
		} finally {
			server.stop()
			tmpDir.deleteRecursively()
		}
	}
}
