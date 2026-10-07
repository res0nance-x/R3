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
		src.createInputStream().buffered().let { istream ->
			DataInputStream(istream).use { stream ->
				var currentOffset = 0L
				try {
					while (true) {
						stream.mark(1)
						val nextByte = stream.read()
						if (nextByte == -1) break
						stream.reset()

						val header = ContentMeta.read(stream)
						if (header.length < 0) {
							throw java.io.IOException("Invalid ContentHeader length ${header.length}")
						}
						val nameBytes = header.name.toByteArray().size
						val typeBytes = header.type.toByteArray().size
						val headerSize = 24L + nameBytes + typeBytes
						val pos = currentOffset + headerSize

						contentList.add(object : Content {
							override val path: String = header.name
							override val ext: String = header.type
							override val lastModified: Long = Instant.now().toEpochMilli()
							override val length: Long = header.length
							override fun createInputStream(): InputStream {
								val itemStream = src.createInputStream()
								itemStream.skipFullBytes(pos)
								return BoundedInputStream(
									itemStream,
									header.length
								)
							}

							override fun toString(): String {
								return "$path $ext $length"
							}
						})

						stream.skipFullBytes(header.length)
						currentOffset = pos + header.length
					}
				} catch (_: java.io.EOFException) {
					// Normal end of pack
				} catch (e: Exception) {
					log("BIdNCUxy5sw: $e")
				}
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