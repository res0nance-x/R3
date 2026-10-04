package r3.http

import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status
import r3.org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestLogRouterTest {

	@Test
	fun testErrorsOnlyFiltering() {
		val loggedMessages = mutableListOf<String>()
		val origLog = r3.io.log
		try {
			r3.io.log = { loggedMessages.add(it) }

			val errorOnlyRouter = RequestLogRouter(errorsOnly = true)
			val allRouter = RequestLogRouter(errorsOnly = false)

			val okHeader = JSONObject().put("method", "GET").put("path", "/api/test").put("remote-ip", "127.0.0.1:1234")
			val notFoundHeader = JSONObject().put("method", "GET").put("path", "/missing").put("remote-ip", "127.0.0.1:1234")
			val serverErrHeader = JSONObject().put("method", "POST").put("path", "/crash").put("remote-ip", "127.0.0.1:1234")

			val okResponse = newFixedLengthResponse(Status.OK, "text/plain", "OK")
			val notFoundResponse = newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found")
			val serverErrResponse = newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Error")

			// With errorsOnly = true:
			errorOnlyRouter.onResponse(okHeader, okResponse)
			assertEquals(0, loggedMessages.size, "200 OK should not be logged when errorsOnly is true")

			errorOnlyRouter.onResponse(notFoundHeader, notFoundResponse)
			assertEquals(1, loggedMessages.size, "404 Not Found should be logged")
			assertTrue(loggedMessages[0].contains("404"))
			assertTrue(loggedMessages[0].contains("/missing"))

			errorOnlyRouter.onResponse(serverErrHeader, serverErrResponse)
			assertEquals(2, loggedMessages.size, "500 Internal Error should be logged")
			assertTrue(loggedMessages[1].contains("500"))

			loggedMessages.clear()

			// With errorsOnly = false:
			allRouter.onResponse(okHeader, okResponse)
			assertEquals(1, loggedMessages.size, "200 OK should be logged when errorsOnly is false")
			assertTrue(loggedMessages[0].contains("200"))
		} finally {
			r3.io.log = origLog
		}
	}
}
