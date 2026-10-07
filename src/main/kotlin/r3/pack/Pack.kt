package r3.pack

import r3.content.Content

interface Pack : Iterable<Content> {
	val size:Int
}