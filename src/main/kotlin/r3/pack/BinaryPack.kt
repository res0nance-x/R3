package r3.pack

import r3.content.Content
import r3.io.*
import r3.source.Sink
import r3.source.Source
import r3.source.copyTo
import java.io.*
import java.util.zip.CRC32

open class BinaryPack(val src: Source) : Pack, Writable {
	protected val contentList = ArrayList<Content>()

	init {
		src.createInputStream().buffered(DEFAULT_COPY_BUFFER_SIZE).let { istream ->
			DataInputStream(istream).use { stream ->
				var currentBlockOffset = 0L
				try {
					while (true) {
						val magic: Int
						try {
							magic = stream.readInt()
						} catch (_: EOFException) {
							break
						}
						if (magic != MAGIC_INDEX) {
							throw IOException("Invalid BinaryPack magic: 0x${Integer.toHexString(magic)}")
						}

						val version = stream.readShort()
						if (version != VERSION) {
							throw IOException("Unsupported BinaryPack version: $version")
						}

						val entryCount = stream.readInt()
						if (entryCount !in 0..MAX_ENTRY_COUNT) {
							throw IOException("Invalid entryCount: $entryCount")
						}

						val totalSpan = stream.readLong()
						if (totalSpan < 0) {
							throw IOException("Invalid totalSpan: $totalSpan")
						}

						val indexDataSize = stream.readInt()
						if (indexDataSize !in 0..MAX_INDEX_DATA_SIZE) {
							throw IOException("Invalid indexDataSize: $indexDataSize")
						}

						val expectedCrc = stream.readInt()
						val indexData = ByteArray(indexDataSize)
						stream.readFully(indexData)

						val actualCrc = CRC32().apply { update(indexData) }.value.toInt()
						if (actualCrc != expectedCrc) {
							throw IOException("CRC32 mismatch in BinaryPack index header")
						}

						val payloadBasePos = currentBlockOffset + HEADER_SIZE + indexDataSize
						var currentRelativeOffset = 0L

						DataInputStream(ByteArrayInputStream(indexData)).use { tableStream ->
							for (i in 0 until entryCount) {
								val flag = tableStream.readByte()
								val name = readSafeString(tableStream, MAX_PATH_LENGTH)
								val type = readSafeString(tableStream, MAX_TYPE_LENGTH)
								val length = tableStream.readLong()
								val created = tableStream.readLong()

								when (flag) {
									FLAG_DELETED -> {
										contentList.removeAll { it.path == name }
									}
									FLAG_ACTIVE -> {
										if (length < 0) {
											throw IOException("Invalid content length: $length")
										}
										val entryPos = payloadBasePos + currentRelativeOffset
										currentRelativeOffset += length

										contentList.add(object : Content {
											override val path: String = name
											override val ext: String = type
											override val lastModified: Long = if (created > 0) created else 0L
											override val length: Long = length
											override fun createInputStream(): InputStream {
												val itemStream = src.createInputStream()
												itemStream.skipFullBytes(entryPos)
												return BoundedInputStream(itemStream, length)
											}

											override fun toString(): String {
												return "$path $ext $length"
											}
										})
									}
									else -> {
										throw IOException("Unknown entry flag: $flag")
									}
								}
							}
						}

						if (currentRelativeOffset != totalSpan) {
							throw IOException("Index totalSpan mismatch: expected $totalSpan, got $currentRelativeOffset")
						}

						stream.skipFullBytes(totalSpan)
						currentBlockOffset = payloadBasePos + totalSpan
					}
				} catch (_: EOFException) {
					// Normal end of pack
				} catch (e: Exception) {
					log(e)
				}
			}
		}
	}

	override fun write(dos: DataOutputStream) {
		src.copyTo(dos)
	}

	override fun iterator(): Iterator<Content> {
		return contentList.iterator()
	}

	companion object {
		const val MAGIC_INDEX = 0x5233504B // "R3PK"
		const val VERSION: Short = 1
		const val HEADER_SIZE = 26L // 4 (magic) + 2 (ver) + 4 (count) + 8 (span) + 4 (size) + 4 (crc)

		const val FLAG_ACTIVE: Byte = 0
		const val FLAG_DELETED: Byte = 1

		const val MAX_PATH_LENGTH = 4096
		const val MAX_TYPE_LENGTH = 128
		const val MAX_ENTRY_COUNT = 10_000_000
		const val MAX_INDEX_DATA_SIZE = 100_000_000 // 100 MB

		private fun writeSafeString(dos: DataOutputStream, s: String, maxLength: Int) {
			val bytes = s.toByteArray(Charsets.UTF_8)
			if (bytes.size > maxLength) {
				throw IOException("String exceeds maximum allowed length of $maxLength: ${bytes.size}")
			}
			dos.writeInt(bytes.size)
			dos.write(bytes)
		}

		private fun readSafeString(dis: DataInputStream, maxLength: Int): String {
			val len = dis.readInt()
			if (len !in 0..maxLength) {
				throw IOException("Invalid string length: $len (max: $maxLength)")
			}
			val bytes = ByteArray(len)
			dis.readFully(bytes)
			return String(bytes, Charsets.UTF_8)
		}

		fun isNewFormat(source: Source): Boolean {
			return try {
				source.createInputStream().use { stream ->
					val buf = ByteArray(4)
					var totalRead = 0
					while (totalRead < 4) {
						val r = stream.read(buf, totalRead, 4 - totalRead)
						if (r < 0) break
						totalRead += r
					}
					totalRead == 4 &&
							buf[0] == 0x52.toByte() &&
							buf[1] == 0x33.toByte() &&
							buf[2] == 0x50.toByte() &&
							buf[3] == 0x4B.toByte() // "R3PK"
				}
			} catch (_: Exception) {
				false
			}
		}

		fun open(source: Source): Pack {
			return if (isNewFormat(source)) {
				BinaryPack(source)
			} else {
				LegacyBinaryPack(source)
			}
		}

		fun createWithProgress(
			content: Iterable<Content>,
			sink: Sink,
			bufferSize: Int = DEFAULT_COPY_BUFFER_SIZE,
			onProgress: (bytesCopied: Long, totalBytes: Long, currentFile: String) -> Unit = { _, _, _ -> }
		) {
			val items = if (content is Collection<Content>) content else content.toList()
			val totalBytes = items.sumOf { it.length }
			val totalFiles = items.size
			val buffer = ByteArray(bufferSize)

			sink.createOutputStream().buffered(bufferSize).use { ostream ->
				DataOutputStream(ostream).use { dos ->
					val tableBaos = ByteArrayOutputStream()
					val tableDos = DataOutputStream(tableBaos)
					var totalSpan = 0L

					for (item in items) {
						tableDos.writeByte(FLAG_ACTIVE.toInt())
						writeSafeString(tableDos, item.path, MAX_PATH_LENGTH)
						writeSafeString(tableDos, item.ext, MAX_TYPE_LENGTH)
						tableDos.writeLong(item.length)
						tableDos.writeLong(item.lastModified)
						totalSpan += item.length
					}
					tableDos.flush()

					val indexData = tableBaos.toByteArray()
					val crc32 = CRC32().apply { update(indexData) }.value.toInt()

					dos.writeInt(MAGIC_INDEX)
					dos.writeShort(VERSION.toInt())
					dos.writeInt(items.size)
					dos.writeLong(totalSpan)
					dos.writeInt(indexData.size)
					dos.writeInt(crc32)
					dos.write(indexData)

					var bytesCopied = 0L
					var fileCount = 0
					for (item in items) {
						item.createInputStream().use { istream ->
							var read = istream.read(buffer)
							while (read >= 0) {
								if (read > 0) {
									dos.write(buffer, 0, read)
									bytesCopied += read
									onProgress(bytesCopied, totalBytes, item.path)
								}
								read = istream.read(buffer)
							}
						}
						fileCount++
						if (totalBytes == 0L) {
							onProgress(fileCount.toLong(), totalFiles.toLong(), item.path)
						} else {
							onProgress(bytesCopied, totalBytes, item.path)
						}
					}
				}
			}
		}

		fun appendWithProgress(
			content: Iterable<Content>,
			sink: Sink,
			bufferSize: Int = DEFAULT_COPY_BUFFER_SIZE,
			onProgress: (bytesCopied: Long, totalBytes: Long, currentFile: String) -> Unit = { _, _, _ -> }
		) {
			createWithProgress(content, sink, bufferSize, onProgress)
		}

		fun create(
			content: Iterable<Content>, sink: Sink,
			progress: (Int) -> Unit = { _ -> }
		) {
			val items = if (content is Collection<Content>) content else content.toList()
			val totalBytes = items.sumOf { it.length }
			val size = items.size.toDouble()
			var count = 0
			createWithProgress(items, sink) { bytesCopied, total, _ ->
				if (total > 0) {
					progress(((bytesCopied.toDouble() / total) * 100).toInt().coerceIn(0, 100))
				} else {
					count++
					if (size > 0) {
						progress(((count / size) * 100).toInt().coerceIn(0, 100))
					}
				}
			}
		}

		fun append(
			content: Iterable<Content>, sink: Sink,
			progress: (Int) -> Unit = { _ -> }
		) {
			create(content, sink, progress)
		}

		fun delete(
			paths: Iterable<String>, sink: Sink
		) {
			val pathList = paths as? Collection<String> ?: paths.toList()
			if (pathList.isEmpty()) return

			sink.createOutputStream().buffered(DEFAULT_COPY_BUFFER_SIZE).use { ostream ->
				DataOutputStream(ostream).use { dos ->
					val tableBaos = ByteArrayOutputStream()
					val tableDos = DataOutputStream(tableBaos)

					for (p in pathList) {
						tableDos.writeByte(FLAG_DELETED.toInt())
						writeSafeString(tableDos, p, MAX_PATH_LENGTH)
						writeSafeString(tableDos, "", MAX_TYPE_LENGTH)
						tableDos.writeLong(0L)
						tableDos.writeLong(System.currentTimeMillis())
					}
					tableDos.flush()

					val indexData = tableBaos.toByteArray()
					val crc32 = CRC32().apply { update(indexData) }.value.toInt()

					dos.writeInt(MAGIC_INDEX)
					dos.writeShort(VERSION.toInt())
					dos.writeInt(pathList.size)
					dos.writeLong(0L) // No payloads
					dos.writeInt(indexData.size)
					dos.writeInt(crc32)
					dos.write(indexData)
				}
			}
		}

		fun compact(
			source: Source, sink: Sink,
			progress: (Int) -> Unit = { _ -> }
		) {
			val pack = BinaryPack(source)
			create(pack, sink, progress)
		}
	}
}
