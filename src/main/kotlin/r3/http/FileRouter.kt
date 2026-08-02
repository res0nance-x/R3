package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status
import r3.content.FileContent
import r3.io.consistentPath
import java.io.File

class FileRouter(
	val rootDir: File,
	val host: String? = null
) : IRouter {
	private val absoluteRootDir: File = rootDir.absoluteFile
	override fun findRoute(session: IHTTPSession): Response? {
		val hostHeader = session.headers["host"] ?: ""
		if (host != null && host != "" && host != hostHeader) {
			return null
		}
		// Only allow GET and HEAD
		if (session.method.name != "GET" && session.method.name != "HEAD") {
			return null
		}
		val rPath = session.uri.substring(1)
		val path = if (rPath == "") {
			"index.html"
		} else if (rPath.endsWith("/")) {
			rPath + "index.html"
		} else {
			rPath
		}
		val filePath = absoluteRootDir.resolve(path).consistentPath()

		if (!filePath.startsWith(absoluteRootDir.consistentPath())) {
			return newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "Forbidden")
		}
		val file = File(filePath)
		// File does not exist or is not a file
		if (!file.exists() || !file.isFile) {
			return null
		}
		return rangeRequestResponse(
			session,
			FileContent(file)
		)
	}
}
