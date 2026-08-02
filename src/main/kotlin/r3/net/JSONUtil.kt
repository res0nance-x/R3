package r3.net

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.request.Method
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status
import r3.io.log
import r3.io.readFixedBytes
import r3.org.json.JSONArray
import r3.org.json.JSONObject

fun parseRequest(session: IHTTPSession): JSONObject {
	return if (session.method == Method.PUT) {
		val length = session.headers["content-length"]?.toInt() ?: 0
		val str = String(session.inputStream.readFixedBytes(length))
		try {
			JSONObject(str)
		} catch (e: Exception) {
			log("v7HadROWSJ4: $e")
			JSONObject()
		}
	} else {
		JSONObject()
	}
}

fun JSONObject.toNanoHttpResponse(): Response {
	val str = this.toString()
	return Response.newFixedLengthResponse(Status.OK, "text/plain", str)
}

fun JSONArray.toNanoHttpResponse(): Response {
	val str = this.toString()
	return Response.newFixedLengthResponse(Status.OK, "text/plain", str)
}

fun createJsonObject(vararg keyVal: Pair<String, String>): JSONObject {
	val obj = JSONObject()
	for (x in keyVal) {
		obj.put(x.first, x.second)
	}
	return obj
}

fun createJsonArray(arr: List<String>): JSONArray {
	val jsonArr = JSONArray()
	for (x in arr) {
		jsonArr.put(x)
	}
	return jsonArr
}

fun createJsonTable(table: List<List<String>>): JSONArray {
	val jsonTable = JSONArray()
	for (row in table) {
		jsonTable.put(createJsonArray(row))
	}
	return jsonTable
}

fun require(request: JSONObject, vararg key: String): Array<String> {
	val arr = Array(key.size) { "" }
	for ((i, x) in key.withIndex()) {
		if (!request.has(x)) {
			log("Missing required parameter $x")
			throw Exception("Missing required parameter $x")
		}
		arr[i] = request.get(x).toString()
	}
	return arr
}
