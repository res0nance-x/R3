package r3.pack

import r3.content.Content

class RAMPack(val list: List<Content> = emptyList()) : Pack {
	constructor(vararg content: Content) : this(content.toList())
	constructor(iterable: Iterable<Content>) : this(iterable.toList())

	override val size: Int
		get() = list.size

	override fun iterator(): Iterator<Content> {
		return list.iterator()
	}
}