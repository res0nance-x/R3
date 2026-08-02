package r3.pack

import r3.content.Content

interface MutablePack : Pack, Iterable<Content> {
	fun add(content: Content)
}