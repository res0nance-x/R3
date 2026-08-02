package r3.content

import r3.http.MimeMap
import r3.source.Source

interface Content : Source {
	val path: String
	val ext: String
	val lastModified: Long // epoch milliseconds
}

val Content.meta: ContentMeta
	get() {
		return ContentMeta(this)
	}

fun Content.getMimeType(): String {
	return MimeMap.getMimeTypeExt(ext)
}