package r3.pack

import r3.content.Content

class PackMap(private val pack: Pack) : Pack {
	// Gets the last result if repeats.
	operator fun get(path: String): Content? {
		var content: Content? = null
		for (c in pack) {
			if (path == c.path) {
				content = c
			}
		}
		return content
	}

	override val size: Int = pack.size
	override fun iterator(): Iterator<Content> {
		return pack.iterator()
	}
}