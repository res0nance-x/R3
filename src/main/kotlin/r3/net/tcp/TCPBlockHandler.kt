package r3.net.tcp

import r3.io.log
import r3.util.srnd
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigInteger

internal class TCPBlockHandler(
	private val tmpDir: File,
	private val maxConcurrentStreams: Int = 10,
	private val maxStreamSize: Long = 1024 * 1024 * 1024, // 1 GB
	private val streamTimeoutMs: Long = 30000, // 30 seconds
	val contentHandler: (TCPNode, ByteArray, File?) -> Unit
) : Closeable {
	class FileStream(val file: File, private val out: OutputStream) : Closeable {
		constructor(file: File) : this(file, FileOutputStream(file).buffered())

		override fun close() {
			out.close()
		}

		fun delete() {
			try {
				out.close()
			} catch (_: Exception) {
			}
			// Attempt to delete the file; on Windows this may fail if another handle is open.
			try {
				if (file.exists()) {
					// Try multiple times with short backoff to handle transient locks (antivirus, indexing, etc.)
					var deleted = false
					repeat(5) {
						if (!file.exists()) {
							deleted = true; return@repeat
						}
						if (file.delete()) {
							deleted = true; return@repeat
						}
						try {
							Thread.sleep(50)
						} catch (_: Exception) {
						}
					}
					if (!deleted) {
						// Schedule eventual cleanup via deleteOnExit as a last resort and log
						try {
							file.deleteOnExit()
						} catch (_: Exception) {
						}
						log("TCPBlockHandler: unable to delete temp file after retries: $file")
					}
				}
			} catch (e: Exception) {
				log("TCPBlockHandler: exception deleting temp file $file: $e")
			}
		}

		fun write(data: ByteArray) {
			out.write(data)
		}
	}

	private class StreamData(
		val header: ByteArray,
		val fileStream: FileStream,
		var lastWrite: Long,
		var bytesWritten: Long = 0L
	)

	private val map = HashMap<Long, StreamData>()
	private fun createTempFile(): File {
		// Use JVM temp file creation inside the dedicated temp base directory to avoid collisions
		return try {
			File.createTempFile("r3tmp_", null, tmpDir)
		} catch (_: Exception) {
			// Fallback to previous approach if createTempFile fails
			val arr = srnd.getByteArray(32)
			val uid = BigInteger(1, arr).toString(32)
			val f = tmpDir.resolve(uid)
			f
		}
	}

	private fun pruneStaleStreams() {
		val now = System.currentTimeMillis()
		val toRemove = mutableListOf<Long>()
		synchronized(map) {
			for ((id, sd) in map) {
				if (now - sd.lastWrite > streamTimeoutMs) {
					toRemove.add(id)
				}
			}
			for (id in toRemove) {
				val sd = map.remove(id)
				if (sd != null) {
					log("TCPBlockHandler: pruning stale stream $id")
					try {
						sd.fileStream.delete()
					} catch (_: Exception) {
					}
				}
			}
		}
	}

	fun handle(node: TCPNode, block: TCPBlock) {
		try {
			pruneStaleStreams()
			val streamData = synchronized(map) {
				map[block.id]
			}
			if (streamData == null) { // If null, this must be a new stream
				if (block.last) { // if last then stream only contains header
					contentHandler(node, block.data, null)
				} else { // if not last then we set out file for writing stream
					synchronized(map) {
						if (map.size >= maxConcurrentStreams) {
							throw java.io.IOException("Too many concurrent streams (max $maxConcurrentStreams)")
						}
						map[block.id] = StreamData(
							block.data, FileStream(createTempFile()),
							System.currentTimeMillis()
						)
					}
				}
			} else {
				// if here then we must have already received the header and so are now writing the stream
				streamData.fileStream.write(block.data)
				streamData.lastWrite = System.currentTimeMillis()
				streamData.bytesWritten += block.data.size
				if (streamData.bytesWritten > maxStreamSize) {
					val err = java.io.IOException("Stream size limit exceeded ($maxStreamSize bytes)")
					log("TCPBlockHandler: $err")
					node.close()
					throw err
				}
				if (block.last) { // if last block then done writing stream
					map.remove(block.id)
					try {
						streamData.fileStream.close()
						// Schedule deletion on JVM exit in case the content handler exits the process
						try {
							streamData.fileStream.file.deleteOnExit()
						} catch (_: Exception) {
						}
						contentHandler(node, streamData.header, streamData.fileStream.file)
						streamData.fileStream.delete()
					} catch (e: Exception) {
						log("TCPBlockHandler: error delivering final file to handler: $e")
						try {
							streamData.fileStream.delete()
						} catch (_: Exception) {
						}
					} finally {
						// If sending to handler fails, ensure file stream is closed and the temp file is deleted
						try {
							streamData.fileStream.delete()
						} catch (_: Exception) {
						}
					}
				}
			}
		} catch (e: Exception) {
			log("TCPBlockHandler: $e")
			// If an exception occurs while processing a block, attempt to clean up the associated stream
			try {
				val sd = synchronized(map) {
					map.remove(block.id)
				}
				if (sd != null) {
					try {
						sd.fileStream.close()
					} catch (_: Exception) {
					}
					try {
						sd.fileStream.file.delete()
					} catch (_: Exception) {
					}
				}
			} catch (_: Exception) {
			}
		}
	}

	override fun close() {
		// Close and remove all in-progress streams
		synchronized(map) {
			for ((_, sd) in map) {
				try {
					sd.fileStream.delete()
				} catch (_: Exception) {
				}
			}
			map.clear()
		}
	}
}