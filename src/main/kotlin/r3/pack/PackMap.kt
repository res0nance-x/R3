package r3.pack

import r3.content.Content

class PackMap(private val list: List<Content>) : Pack {
	constructor(iterable: Iterable<Content>) : this(iterable.toList())

	private val map: Map<String, Content> = list.associateBy { it.path }

	// Gets the last result if repeats.
	operator fun get(path: String): Content? = map[path]

	override val size: Int = list.size
	override fun iterator(): Iterator<Content> {
		return list.iterator()
	}
}