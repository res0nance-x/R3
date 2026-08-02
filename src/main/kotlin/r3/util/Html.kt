package r3.util

import java.net.URL
import java.net.URLDecoder

val URL.queryParams: Map<String, String>
	get() {
		val params = mutableMapOf<String, String>()
		val pairs = query.split("&")
		for (pair in pairs) {
			val idx = pair.indexOf("=")
			val key = if (idx > 0) {
				URLDecoder.decode(pair.substring(0, idx), "UTF-8")
			} else {
				pair
			}
			val value = if (idx > 0 && pair.length > idx + 1) {
				URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
			} else {
				null
			}
			if (value != null) {
				params[key] = value
			}
		}
		return params
	}