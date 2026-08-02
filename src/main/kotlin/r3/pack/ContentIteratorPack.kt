package r3.pack

import r3.content.Content

class ContentIteratorPack(iterable: Iterable<Content>) : Pack {
	val map = LinkedHashMap<String, Content>()

	init {
		iterable.forEach {
			map[it.path] = it
		}
	}

	override val size: Int = map.size
	override val keys: Set<String> = map.keys
	override fun get(key: String): Content? {
		return map[key]
	}

	override fun visit(visitor: (String, Content) -> Unit) {
		map.forEach(visitor)
	}
}