package r3.pack

import r3.content.Content

/* PackMap assumes unique Content paths. This doesn't have to be the case for generic pack files, which
allows repeated paths, but is typical for many Pack wrappers such as DirPack, ZipPack and others.
PackMap is useful when we want to represent a pack as a path-keyed file container e.g to serve
with as a path-keyed web resource container for instance*/

class PackMap(private val list: List<Content>) : Pack {
	constructor(iterable: Iterable<Content>) : this(iterable.toList())

	// Later entries overwrite previous entries with the same path value
	private val map: Map<String, Content> = list.associateBy { it.path }

	// Gets the last result if repeats.
	operator fun get(path: String): Content? = map[path]
	override val size: Int = list.size
	override fun iterator(): Iterator<Content> {
		return list.iterator()
	}
}