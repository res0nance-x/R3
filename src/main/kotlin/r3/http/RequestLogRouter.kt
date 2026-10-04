package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import r3.content.Content
import r3.io.log
import r3.org.json.JSONObject
import r3.util.humanReadableSize
import java.text.SimpleDateFormat
import java.util.*

open class RequestLogRouter(val errorsOnly: Boolean = false) : ContentHandler {
	companion object : RequestLogRouter(false) {
		val df = SimpleDateFormat("EEE-HH:mm")
	}

	override fun handle(header: JSONObject, content: Content?): Content? {
		return null
	}

	override fun onResponse(header: JSONObject, response: Response) {
		val status = response.status.requestStatus
		val isError = status in 400..599
		if (errorsOnly && !isError) {
			return
		}
		log(format(header, response))
	}

	fun format(session: IHTTPSession, response: Response? = null): String {
		val method = session.method?.name ?: ""
		val countryCode = session.headers["cf-ipcountry"]?.let { "($it)" } ?: ""
		val queryPart = session.queryParameterString ?: ""
		val contentLength = session.headers["content-length"]?.toLongOrNull()?.let {
			if (it > 0L) ", " + it.humanReadableSize() else ""
		} ?: ""
		val ip = session.headers["cf-connecting-ip"]
			?: session.remoteInetSocketAddress?.address?.toString()?.drop(1)
			?: ""
		val time = df.format(Date(System.currentTimeMillis()))
		val statusPart = response?.status?.let { " [${it.requestStatus} ${it.description}]" } ?: ""
		return "$ip$countryCode, $time, $method:${session.uri}" +
				(if (queryPart.isNotEmpty()) "?$queryPart" else "") +
				"$contentLength$statusPart"
	}

	fun format(header: JSONObject, response: Response? = null): String {
		val method = header.optString("method", "GET")
		val path = header.optString("path", "/")
		val countryCode = header.optString("cf-ipcountry", "").let { if (it.isNotEmpty()) "($it)" else "" }
		val ip = header.optString("cf-connecting-ip").takeIf { it.isNotEmpty() }
			?: header.optString("remote-ip")
		val param = header.optJSONObject("param")
		val paramStr = if (param != null && !param.isEmpty) " $param" else ""
		val time = df.format(Date(System.currentTimeMillis()))
		val statusPart = response?.status?.let { " [${it.requestStatus} ${it.description}]" } ?: ""
		return "$ip$countryCode, $time, $method:$path$paramStr$statusPart"
	}
}