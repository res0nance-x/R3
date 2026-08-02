package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status

/**
 * Router that serves `/favicon.ico` from the application's classpath resources.
 * If the resource `favicon.ico` is not found on the classpath, this router returns null
 * so subsequent routers may handle the request.
 */
class FavIconRouter(private val path: String = "/favicon.ico") : IRouter {

    private fun isPng(bytes: ByteArray): Boolean {
        return bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() && bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() && bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
    }

    override fun findRoute(session: IHTTPSession): Response? {
        // Only respond to exact path match
        if (!session.uri.equals(path, ignoreCase = true)) return null

        // Only allow GET and HEAD
        if (session.method.name != "GET" && session.method.name != "HEAD") {
            return newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, "text/plain", "Method Not Allowed")
        }

        // Attempt to load favicon.ico from classpath
        val resName = "favicon.ico"
        val cl = javaClass.classLoader
        val stream = cl?.getResourceAsStream(resName) ?: return null

        val bytes = try {
            stream.use { it.readBytes() }
        } catch (_: Exception) {
            return null
        }

        val mime = if (isPng(bytes)) "image/png" else "image/x-icon"
        val body = if (session.method.name == "HEAD") ByteArray(0) else bytes
        val resp = newFixedLengthResponse(Status.OK, mime, body)
        resp.addHeader("Cache-Control", "public, max-age=86400")
        return resp
    }
}
