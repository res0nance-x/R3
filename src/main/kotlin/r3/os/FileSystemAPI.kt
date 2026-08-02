package r3.os

import r3.content.Content
import r3.source.Sink
import r3.source.Source

interface FileSystemAPI {
	fun selectToWrite(title: String, name: String?, ext: String?, success: (Sink) -> Unit)
	fun selectToRead(title: String, ext: String?, success: (Content) -> Unit)
	fun selectMultipleToRead(title: String, ext: String?, success: (Content) -> Unit)
	fun download(name: String, source: Source)
}