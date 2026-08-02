package r3.pack
//open class MutableBinaryPack(src: Source, sink: Sink) : BinaryPack(src), MutablePack, Closeable {
//	private val dos = DataOutputStream(BufferedOutputStream(sink.createOutputStream()))
//	fun set(key: Key256, value: Content) {
//		remove(key)
//		value.write(dos)
//		map[value.key] = value
//	}
//
//	fun remove(key: Key256) {
//		get(key)?.also { content ->
//			map.remove(key)
//			ContentMeta(key, content.name, content.contentType, 0).write(dos)
//		}
//	}
//
//	override fun add(content: Content) {
//		remove(content.key)
//		content.write(dos)
//		map[content.key] = content
//	}
//
//	override fun close() {
//		dos.close()
//	}
//}