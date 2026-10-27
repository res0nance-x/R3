package r3.pack
 
import r3.content.Content
import java.net.URLDecoder
import java.net.URLEncoder

/* PackMap assumes unique Content paths. This doesn't have to be the case for generic pack files, which
allows repeated paths, but is typical for many Pack wrappers such as DirPack, ZipPack and others.
PackMap is useful when we want to represent a pack as a path-keyed file container e.g. to serve
with as a path-keyed web resource container for instance*/

class PackMap(private val list: List<Content>) : Pack {
	constructor(iterable: Iterable<Content>) : this(iterable.toList())

	// Later entries overwrite previous entries with the same path value
	private val map: Map<String, Content> = list.associateBy { it.path }

	// Fallback map for percent-decoded / percent-encoded variations
	private val altMap: Map<String, Content> by lazy {
		val result = HashMap<String, Content>()
		for (item in list) {
			val path = item.path
			val enc = encodePath(path)
			if (enc != path) result.putIfAbsent(enc, item)
			val dec = runCatching { URLDecoder.decode(path, Charsets.UTF_8.name()) }.getOrNull()
			if (dec != null && dec != path) result.putIfAbsent(dec, item)
		}
		result
	}

	// Gets the last result if repeats.
	operator fun get(path: String): Content? {
		map[path]?.let { return it }
		altMap[path]?.let { return it }

		val encoded = encodePath(path)
		if (encoded != path) {
			map[encoded]?.let { return it }
		}

		val decoded = runCatching { URLDecoder.decode(path, Charsets.UTF_8.name()) }.getOrNull()
		if (decoded != null && decoded != path) {
			map[decoded]?.let { return it }
		}

		return null
	}

	val size: Int = list.size
	override fun iterator(): Iterator<Content> {
		return list.iterator()
	}

	companion object {
		fun encodePath(path: String): String {
			return path.split('/').joinToString("/") { seg ->
				URLEncoder.encode(seg, Charsets.UTF_8.name()).replace("+", "%20")
			}
		}
	}
}