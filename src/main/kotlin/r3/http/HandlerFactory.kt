package r3.http

import org.nanohttpd.protocols.http.response.Response
import r3.content.*
import r3.io.log
import r3.key.Key128
import r3.org.json.JSONArray
import r3.org.json.JSONObject
import r3.pack.Pack
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun resolveFile(dir: File, path: String): File {
	val baseStr = dir.absoluteFile.normalize().invariantSeparatorsPath
	val targetFile = File(dir, path).absoluteFile.normalize()
	val targetStr = targetFile.invariantSeparatorsPath
	val basePrefix = if (baseStr.endsWith('/')) baseStr else "$baseStr/"
	val isValid = targetStr == baseStr || targetStr.startsWith(basePrefix)

	if (!isValid) {
		error("Invalid path $path")
	}
	return targetFile
}

fun convertStringToType(input: String, targetType: Class<*>): Any {
	if (input == "") {
		return when (targetType) {
			String::class.java -> ""
			Int::class.java, Integer::class.java, Integer.TYPE -> 0
			Boolean::class.java, java.lang.Boolean::class.java, java.lang.Boolean.TYPE -> false
			Char::class.java, Character::class.java, Character.TYPE -> ' '
			File::class.java -> File(".")
			else -> throw IllegalArgumentException("Unsupported parameter type: $targetType")
		}
	}
	return when (targetType) {
		String::class.java -> input
		Int::class.java, Integer::class.java, Integer.TYPE -> input.toInt()
		Boolean::class.java, java.lang.Boolean::class.java, java.lang.Boolean.TYPE -> input.toBoolean()
		Char::class.java, Character::class.java, Character.TYPE -> input.single()
		File::class.java -> File(input)
		else -> throw IllegalArgumentException("Unsupported parameter type: $targetType")
	}
}

fun buildPipelineFromCli(cliInput: String): List<ContentHandler> {
	// 1. Group available factories by their annotation name using Java reflection (supports multiple overloads)
	val factoryMap = HandlerFactory::class.java.declaredMethods
		.filter { it.isAnnotationPresent(ContentHandlerAnnotation::class.java) }
		.groupBy { it.getAnnotation(ContentHandlerAnnotation::class.java).name }
	// 2. Regex to extract: name and optional arguments inside brackets -> name[args] or name
	val regex = Regex("""(\w+)(?:\[([^\]]*)\])?""")
	val matches = regex.findAll(cliInput)

	return matches.map { match ->
		val handlerName = match.groupValues[1]
		val rawArgsStr = match.groupValues.getOrNull(2)
		val rawArgs = if (rawArgsStr.isNullOrBlank()) {
			emptyList()
		} else {
			rawArgsStr.split(",").map { it.trim() }
		}
		val methods = factoryMap[handlerName]
			?: throw IllegalArgumentException("Unknown handler: $handlerName")
		// Match target overload based on arg count
		val method = methods.firstOrNull { it.parameterTypes.size == rawArgs.size }
			?: methods.sortedByDescending { it.parameterTypes.size }.firstOrNull { it.parameterTypes.size <= rawArgs.size }
			?: methods.minByOrNull { it.parameterTypes.size }!!
		// 3. Match raw CLI strings to actual function parameter types
		val parameterTypes = method.parameterTypes
		val convertedArgs = parameterTypes.mapIndexed { index, paramType ->
			val rawValue = rawArgs.getOrNull(index) ?: ""
			convertStringToType(rawValue, paramType)
		}
		// 4. Invoke the factory function.
		method.invoke(HandlerFactory, *convertedArgs.toTypedArray()) as ContentHandler
	}.toList()
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ContentHandlerAnnotation(
	val name: String
)

object HandlerFactory {
	private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

	@ContentHandlerAnnotation("log")
	fun createLogRouter(): ContentHandler {
		return ContentHandler { header: JSONObject, content: Content? ->
			val param = header.optJSONObject("param")
			val paramStr = param?.toString() ?: ""
			val sb = StringBuilder()
			sb.append(LocalDateTime.now().format(dtf)).append(", ")
			sb.append(header.optString("remote-ip")).append(", ")
			sb.append(header.getString("path")).append(" ").append(paramStr)
			log(sb.toString())
			if (content != null) {
				val contentMeta = content.meta.toString()
				log(contentMeta)
			}
			null
		}
	}

	// prefix must start and end with '/'
	@JvmOverloads
	@ContentHandlerAnnotation("file")
	fun createFileHandler(dir: File, prefix: String = "/"): ContentHandler {
		val normPrefix = "/" + prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
		return object : ContentHandler {
			override fun handle(header: JSONObject, content: Content?): Content? {
				val path = header.optString("path", null)
				// Match exact prefix, subpath, or prefix without the trailing slash
				val matchesPrefix = path.startsWith(normPrefix) || path == normPrefix.removeSuffix("/")
				return if (matchesPrefix) {
					val subPath = if (path.startsWith(normPrefix)) path.substring(normPrefix.length) else ""
					val file = resolveFile(dir, subPath)
					if (file.isFile) {
						FileContent(file)
					} else {
						null
					}
				} else {
					null
				}
			}

			override fun onResponse(header: JSONObject, response: Response) {
				val path = header.optString("path", "")
				if (path.endsWith(".html", ignoreCase = true) || path.endsWith("/") || path.isEmpty()) {
					// Configured at handler level because WebServer only invokes onResponse on the
					// handler that produces content. App server policy permits local WS/HTTP API connections
					// while blocking all remote network access.
					response.addHeader(
						"Content-Security-Policy",
						"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; img-src 'self' data: blob:; media-src 'self' data: blob:; connect-src 'self' ws: http://localhost:* http://127.0.0.1:*; object-src 'none'; base-uri 'self';"
					)
				}
			}
		}
	}

	// prefix must start and end with '/'
	// Like the file handler except will generate directory listings HTML
	@JvmOverloads
	@ContentHandlerAnnotation("dir")
	fun createDirectoryRouter(dir: File, prefix: String = "/"): ContentHandler {
		val normPrefix = "/" + prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
		return ContentHandler { header: JSONObject, content: Content? ->
			val path = header.optString("path", null)
			// Match exact prefix, subpath, or prefix without the trailing slash
			val matchesPrefix = path.startsWith(normPrefix) || path == normPrefix.removeSuffix("/")
			if (matchesPrefix) {
				val subPath = if (path.startsWith(normPrefix)) path.substring(normPrefix.length) else ""
				val file = resolveFile(dir, subPath)
				if (file.isDirectory) {
					val json = JSONArray()
					file.listFiles()?.forEach { file ->
						val fileJSON = JSONObject()
						val isDir = file.isDirectory
						fileJSON.put("name", if (isDir) file.name + "/" else file.name)
						json.put(fileJSON)
					}
					BinaryContent(
						dirHTMLTemplate(json).toByteArray(), "html", "html"
					)
				} else {
					FileContent(file)
				}
			} else {
				null
			}
		}
	}

	@JvmOverloads
	@ContentHandlerAnnotation("res")
	fun createClasspathResourceHandler(resourceDir: String, prefix: String = "/"): ContentHandler {
		val normPrefix = "/" + prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
		val normResourceDir = resourceDir.trim('/')
		return ContentHandler { header: JSONObject, content: Content? ->
			val path = header.optString("path", null)
			val matchesPrefix = path.startsWith(normPrefix) || path == normPrefix.removeSuffix("/")
			if (matchesPrefix) {
				val subPath = if (path.startsWith(normPrefix)) path.substring(normPrefix.length) else ""
				val cleanPath =
					if (normResourceDir.isEmpty()) subPath.trim('/') else "$normResourceDir/${subPath.trim('/')}"
				val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(cleanPath)
					?: HandlerFactory::class.java.classLoader.getResourceAsStream(cleanPath)
				if (stream != null) {
					val bytes = stream.use { it.readBytes() }
					val fileName = cleanPath.substringAfterLast('/')
					val ext = fileName.substringAfterLast('.', "")
					BinaryContent(bytes, fileName, ext)
				} else {
					null
				}
			} else {
				null
			}
		}
	}

	fun createPackHandler(pack: Pack): ContentHandler {
		return object : ContentHandler {
			override fun handle(header: JSONObject, content: Content?): Content? {
				val path = header.optString("path") ?: error("No Path")
				val key = path.substring(1)
				return if (key == "pack_list") {
					val json = JSONArray()
					for (k in pack.keys) {
						val itemContent = pack[k] ?: BinaryContent(ByteArray(0), "empty.bin", "bin")
						json.put(JSONObject().apply {
							put("path", itemContent.path)
							put("type", itemContent.getMimeType())
							put("length", itemContent.length)
							put("lastModified", itemContent.lastModified)
						})
					}
					BinaryContent(json.toString(2).toByteArray(), "pack_list.json", "json")
				} else {
					pack[key]
				}
			}

			override fun onResponse(header: JSONObject, response: Response) {
				val path = header.optString("path", "")
				if (path.endsWith(".html", ignoreCase = true) || path.endsWith("/") || path.isEmpty()) {
					// PackViewer uses a distinct policy: allows unsafe-eval/inline for third-party pack
					// content/templates, but strictly blocks network connections (connect-src 'self' only).
					response.addHeader(
						"Content-Security-Policy",
						"default-src 'self' data: blob: 'unsafe-inline' 'unsafe-eval'; img-src 'self' data: blob:; media-src 'self' data: blob:; connect-src 'self'; font-src 'self' data:; object-src 'none'; base-uri 'self';"
					)
				}
			}
		}
	}

	fun createWelcomeHandler(): ContentHandler {
		return object : ContentHandler {
			override fun handle(header: JSONObject, content: Content?): Content? {
				if (header.optString("path") == "/") {
					return HTMLContent(
						"""
						<!DOCTYPE html>
						<html>
						<head>
							<meta http-equiv="refresh" content="0; url=index.html">
							<title>Redirecting...</title>
						</head>
						<body>
							<p>Redirecting to <a href="index.html">index.html</a>...</p>
						</body>
						</html>
					""".trimIndent()
					)
				}
				return null
			}

			override fun onResponse(header: JSONObject, response: Response) {
				// Matches PackViewer CSP policy
				response.addHeader(
					"Content-Security-Policy",
					"default-src 'self' data: blob: 'unsafe-inline' 'unsafe-eval'; img-src 'self' data: blob:; media-src 'self' data: blob:; connect-src 'self'; font-src 'self' data:; object-src 'none'; base-uri 'self';"
				)
			}
		}
	}

	@JvmOverloads
	fun createHostOriginHandler(allowedHosts: Set<String> = setOf("localhost", "127.0.0.1", "::1")): HostOriginHandler {
		return HostOriginHandler(allowedHosts)
	}

	@JvmOverloads
	fun createAuthHandler(
		startupKey: Key128 = Key128.randomKey(),
		authSecret: Key128 = Key128.randomKey(),
		cookieName: String = "graffiti_token",
		redirectTo: String = "/index.html"
	): AuthorizationHandler {
		return AuthorizationHandler(startupKey, authSecret, cookieName, redirectTo)
	}
}