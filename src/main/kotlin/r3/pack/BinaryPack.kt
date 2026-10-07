package r3.pack

import r3.content.Content
import r3.content.ContentMeta
import r3.io.*
import r3.source.Sink
import r3.source.Source
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.time.Instant

open class BinaryPack(val src: Source) : Pack, Writable {
	protected val contentList = ArrayList<Content>()

	init {
		val cis = CountingInputStream(src.createInputStream())
		DataInputStream(cis).use { stream ->
			try {
				while (cis.count < src.length - 1) {
					val header = ContentMeta.read(stream)
					if (header.length < 0) {
						throw Exception("Invalid ContentHeader length ${header.length}")
					} else {
						contentList.add(object : Content {
							override val path: String = header.name
							override val ext: String = header.type
							override val lastModified: Long = Instant.now().toEpochMilli()
							private val pos = cis.count
							override fun createInputStream(): InputStream {
								val istream = src.createInputStream()
								istream.skipFullBytes(pos)
								return BoundedInputStream(
									istream,
									header.length
								)
							}

							override val length: Long = header.length
							override fun toString(): String {
								return "$path $ext $length"
							}
						})
						cis.skipFullBytes(header.length)
					}
				}
			} catch (e: Exception) {
				log("BIdNCUxy5sw: $e")
			}
		}
	}

	override val size: Int = contentList.size

	override fun write(dos: DataOutputStream) {
		src.createInputStream().use {
			it.copyTo(dos)
		}
	}

	override fun iterator(): Iterator<Content> {
		return contentList.iterator()
	}

	companion object {
		fun create(
			content: Iterable<Content>, sink: Sink,
			progress: (Int) -> Unit = { _ -> }
		) {
			sink.createOutputStream().buffered().use { ostream ->
				val size = content.count().toDouble()
				DataOutputStream(ostream).use { dos ->
					var count = 0
					content.forEach { content ->
						ContentMeta(content).write(dos)
						content.createInputStream().use { it.copyTo(dos) }
						++count
						progress((count.toDouble() / size * 100).toInt())
					}
				}
			}
		}
	}
}