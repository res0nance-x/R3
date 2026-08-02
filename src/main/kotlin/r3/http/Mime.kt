package r3.http

import r3.io.getExtension
import java.util.*

const val mimeTypeStr = """
bin,application/octet-stream
html,text/html;charset=utf-8
htm,text/html;charset=utf-8
jpg,image/jpeg
jpeg,image/jpeg
png,image/png
gif,image/gif
bmp,image/bmp
webp,image/webp
svg,image/svg+xml
ico,image/x-icon
mp3,audio/mpeg
mp4,video/mp4
m4v,video/mp4
mpg,video/mpeg
mpeg,video/mpeg
mkv,video/x-matroska
webm,video/webm
3gp,video/3gpp
avi,video/x-msvideo
flac,audio/flac
wav,audio/wav
mid,audio/midi
midi,audio/midi
oga,audio/ogg
opus,audio/opus
ogv,video/ogg
ogx,application/ogg
weba,audio/webm
pdf,application/pdf
json,application/json
jsonld,application/ld+json
xml,application/xml
xhtml,application/xhtml+xml
csv,text/csv
ts,text/plain;charset=utf-8
txt,text/plain;charset=utf-8
rtf,application/rtf
doc,application/msword
docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document
ppt,application/vnd.ms-powerpoint
pptx,application/vnd.openxmlformats-officedocument.presentationml.presentation
xls,application/vnd.ms-excel
xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
odt,application/vnd.oasis.opendocument.text
epub,application/epub+zip
zip,application/zip
rar,application/vnd.rar
gz,application/gzip
bz,application/x-bzip
bz2,application/x-bzip2
tar,application/x-tar
7z,application/x-7z-compressed
css,text/css
js,text/javascript
java,text/plain;charset=utf-8
kt,text/plain;charset=utf-8
"""

object MimeMap : HashMap<String, String>() {
	private fun readResolve(): Any = MimeMap

	init {
		mimeTypeStr.lineSequence().forEach {
			val line = it.trim()
			if (line.isNotBlank()) {
				val index = line.indexOf(',')
				val ext = line.substring(0, index)
				val mime = line.substring(index + 1)
				this[ext] = mime
			}
		}
	}

	fun getMimeTypeExt(ext: String): String {
		val mimeType = this[ext]
		if (mimeType != null) {
			return mimeType
		}
		return "application/octet-stream"
	}

	fun getMimeType(path: String): String {
		val ext = getExtension(path).lowercase(Locale.getDefault())
		return getMimeTypeExt(ext)
	}

	fun getExtensionForMimeType(mimeType: String): String {
		val target = mimeType.lowercase(Locale.getDefault())
		if (target.startsWith("text/plain")) {
			return "txt"
		}
		if (target.startsWith("text/html")) {
			return "html"
		}
		return entries.firstOrNull { it.value.lowercase(Locale.getDefault()) == target }?.key ?: ""
	}
}