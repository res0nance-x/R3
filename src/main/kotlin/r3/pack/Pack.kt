package r3.pack

import r3.collection.SimpleMap
import r3.content.Content

interface Pack : SimpleMap<String, Content>, Iterable<Content> {
	override operator fun iterator(): Iterator<Content> {
		val list = ArrayList<Content>()
		this.visit { k, c ->
			list.add(c)
		}
		return list.iterator()
	}
}