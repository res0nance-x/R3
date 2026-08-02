package r3.app

import r3.content.Content

interface UIHandler {
	fun handle(content: Content)
}