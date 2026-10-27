package r3.http

import r3.io.getExtension
import java.util.*

const val mimeTypeStr = """
# Default fallback
bin,application/octet-stream

# HTML, XHTML & Dynamic Web Templates
html,text/html;charset=utf-8
htm,text/html;charset=utf-8
xhtml,application/xhtml+xml
shtml,text/html;charset=utf-8
shtm,text/html;charset=utf-8
php,text/html;charset=utf-8
phtml,text/html;charset=utf-8
php4,text/html;charset=utf-8
php5,text/html;charset=utf-8
do,text/html;charset=utf-8
action,text/html;charset=utf-8
jsp,text/html;charset=utf-8
jspx,text/html;charset=utf-8
asp,text/html;charset=utf-8
aspx,text/html;charset=utf-8
cfm,text/html;charset=utf-8
cfc,text/html;charset=utf-8
cgi,text/html;charset=utf-8
pl,text/html;charset=utf-8

# Stylesheets, Scripts & Web Assets
css,text/css
js,text/javascript
mjs,text/javascript
cjs,text/javascript
wasm,application/wasm
map,application/json
json,application/json
jsonld,application/ld+json
xml,application/xml
webmanifest,application/manifest+json
rss,application/rss+xml
atom,application/atom+xml

# Images
jpg,image/jpeg
jpeg,image/jpeg
png,image/png
gif,image/gif
webp,image/webp
avif,image/avif
svg,image/svg+xml
ico,image/x-icon
bmp,image/bmp

# Fonts
woff,font/woff
woff2,font/woff2
ttf,font/ttf
otf,font/otf
eot,application/vnd.ms-fontobject

# Audio
mp3,audio/mpeg
aac,audio/aac
m4a,audio/mp4
oga,audio/ogg
opus,audio/opus
flac,audio/flac
wav,audio/wav
weba,audio/webm
mid,audio/midi
midi,audio/midi

# Video
mp4,video/mp4
m4v,video/mp4
webm,video/webm
mkv,video/x-matroska
avi,video/x-msvideo
mpg,video/mpeg
mpeg,video/mpeg
ogv,video/ogg
3gp,video/3gpp

# Documents & Text
txt,text/plain;charset=utf-8
ts,text/plain;charset=utf-8
md,text/markdown;charset=utf-8
csv,text/csv
rtf,application/rtf
pdf,application/pdf
doc,application/msword
docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document
ppt,application/vnd.ms-powerpoint
pptx,application/vnd.openxmlformats-officedocument.presentationml.presentation
xls,application/vnd.ms-excel
xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
odt,application/vnd.oasis.opendocument.text
epub,application/epub+zip

# Archives & Compression
zip,application/zip
tar,application/x-tar
gz,application/gzip
bz,application/x-bzip
bz2,application/x-bzip2
7z,application/x-7z-compressed
rar,application/vnd.rar
ogx,application/ogg

# Source Code
java,text/plain;charset=utf-8
kt,text/plain;charset=utf-8
"""

object MimeMap : HashMap<String, String>() {
	private fun readResolve(): Any = MimeMap

	init {
		mimeTypeStr.lineSequence().forEach {
			val line = it.trim()
			if (line.isNotBlank() && !line.startsWith("#") && line.contains(',')) {
				val index = line.indexOf(',')
				val ext = line.substring(0, index).trim()
				val mime = line.substring(index + 1).trim()
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