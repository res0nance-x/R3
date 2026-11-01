package r3.net.tcp

import r3.io.log
import r3.util.srnd
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

typealias FileStream = TCPFileStream

/**
 * Full-featured file-backed [BlockHandler] supporting multi-node stream tracking,
 * custom [TCPFileStream] factories (e.g. for partial/resumable transfers),
 * progress reporting, and size/timeout enforcement.
 */
class TCPBlockHandler(
	private val tmpDir: File,
	private val maxConcurrentStreams: Int = 10,
	private val maxStreamSize: Long = 1024 * 1024 * 1024, // 1 GB
	private val streamTimeoutMs: Long = 300000, // 5 minutes
	val fileStreamFactory: ((TCPNode, ByteArray) -> TCPFileStream)? = null,
	@Volatile var onStreamProgress: ((node: TCPNode, streamId: Long, header: ByteArray, bytesWritten: Long) -> Unit)? = null,
	val contentHandler: (TCPNode, ByteArray, File?) -> Unit
) : BlockHandler {
	private class StreamData(
		val header: ByteArray,
		val fileStream: FileStream,
		var lastWrite: Long,
		var bytesWritten: Long = 0L,
		var lastProgressReport: Long = 0L
	)

	private val nodeMaps = ConcurrentHashMap<TCPNode, HashMap<Long, StreamData>>()
	private val deadStreams = ConcurrentHashMap<TCPNode, MutableSet<Long>>()

	private fun streamsFor(node: TCPNode): HashMap<Long, StreamData> =
		nodeMaps.computeIfAbsent(node) { HashMap() }

	private fun deadStreamsFor(node: TCPNode): MutableSet<Long> =
		deadStreams.computeIfAbsent(node) { ConcurrentHashMap.newKeySet() }

	override fun activeStreamsCount(node: TCPNode): Int {
		val map = nodeMaps[node] ?: return 0
		return synchronized(map) { map.size }
	}

	private fun createTempFile(): File {
		// Use JVM temp file creation inside the dedicated temp base directory to avoid collisions
		return try {
			File.createTempFile("r3tmp_", null, tmpDir)
		} catch (_: Exception) {
			// Fallback to previous approach if createTempFile fails
			val arr = srnd.getByteArray(32)
			val uid = BigInteger(1, arr).toString(32)
			tmpDir.resolve(uid)
		}
	}

	private fun pruneStaleStreams(node: TCPNode) {
		val map = nodeMaps[node] ?: return
		val dead = deadStreamsFor(node)
		val now = System.currentTimeMillis()
		val toRemove = mutableListOf<Long>()
		val prunedStreams = mutableListOf<StreamData>()
		synchronized(map) {
			for ((id, sd) in map) {
				if (now - sd.lastWrite > streamTimeoutMs) {
					toRemove.add(id)
				}
			}
			for (id in toRemove) {
				val sd = map.remove(id)
				if (sd != null) {
					prunedStreams.add(sd)
					if (dead.size > 200) {
						dead.clear()
					}
					dead.add(id)
					log("TCPBlockHandler: pruning stale stream $id")
				}
			}
		}
		for (sd in prunedStreams) {
			try {
				if (sd.fileStream.deleteOnClose) {
					sd.fileStream.delete()
				} else {
					sd.fileStream.close()
				}
			} catch (_: Exception) {
			}
		}
		if (prunedStreams.isNotEmpty()) {
			node.notifyTransferState()
		}
	}

	override fun handle(node: TCPNode, block: TCPBlock) {
		try {
			pruneStaleStreams(node)
			val dead = deadStreamsFor(node)
			if (dead.contains(block.id)) {
				if (block.last) {
					dead.remove(block.id)
				}
				return
			}
			val map = streamsFor(node)
			val streamData = synchronized(map) {
				map[block.id]
			}
			if (streamData == null) { // If null, this must be a new stream
				if (block.last) { // if last then stream only contains header
					contentHandler(node, block.data, null)
				} else { // if not last then we set out file for writing stream
					synchronized(map) {
						if (map.size >= maxConcurrentStreams) {
							throw IOException("Too many concurrent streams (max $maxConcurrentStreams)")
						}
						val fs = fileStreamFactory?.invoke(node, block.data) ?: FileStream(createTempFile())
						val initialBytes = if (fs.file.exists()) fs.file.length() else 0L
						map[block.id] = StreamData(
							block.data, fs,
							System.currentTimeMillis(),
							bytesWritten = initialBytes
						)
					}
					node.notifyTransferState()
				}
			} else {
				// if here then we must have already received the header and so are now writing the stream
				streamData.fileStream.write(block.data)
				val now = System.currentTimeMillis()
				streamData.lastWrite = now
				streamData.bytesWritten += block.data.size
				if (streamData.bytesWritten > maxStreamSize) {
					dead.add(block.id)
					val err = IOException("Stream size limit exceeded ($maxStreamSize bytes)")
					log("TCPBlockHandler: $err")
					node.close()
					throw err
				}
				if (now - streamData.lastProgressReport >= 250 || block.last) {
					streamData.lastProgressReport = now
					try {
						onStreamProgress?.invoke(node, block.id, streamData.header, streamData.bytesWritten)
					} catch (_: Exception) {
					}
				}
				if (block.last) { // if last block then done writing stream
					synchronized(map) {
						map.remove(block.id)
					}
					node.notifyTransferState()
					try {
						streamData.fileStream.close()
						// Schedule deletion on JVM exit in case the content handler exits the process
						try {
							streamData.fileStream.file.deleteOnExit()
						} catch (_: Exception) {
						}
						contentHandler(node, streamData.header, streamData.fileStream.file)
						if (streamData.fileStream.deleteOnClose) {
							streamData.fileStream.delete()
						}
					} catch (e: Exception) {
						log("TCPBlockHandler: error delivering final file to handler: $e")
						try {
							if (streamData.fileStream.deleteOnClose) {
								streamData.fileStream.delete()
							}
						} catch (_: Exception) {
						}
					} finally {
						// If sending to handler fails, ensure file stream is closed and the temp file is deleted
						try {
							if (streamData.fileStream.deleteOnClose) {
								streamData.fileStream.delete()
							}
						} catch (_: Exception) {
						}
					}
				}
			}
		} catch (e: Exception) {
			log("TCPBlockHandler: $e")
			// If an exception occurs while processing a block, attempt to clean up the associated stream
			try {
				val map = nodeMaps[node]
				val sd = if (map != null) {
					synchronized(map) { map.remove(block.id) }
				} else null
				if (sd != null) {
					node.notifyTransferState()
					try {
						sd.fileStream.close()
					} catch (_: Exception) {
					}
					try {
						if (sd.fileStream.deleteOnClose) {
							sd.fileStream.file.delete()
						}
					} catch (_: Exception) {
					}
				}
			} catch (_: Exception) {
			}
		}
	}

	override fun onNodeClosed(node: TCPNode) {
		deadStreams.remove(node)
		val map = nodeMaps.remove(node) ?: return
		val streams = synchronized(map) {
			val list = map.values.toList()
			map.clear()
			list
		}
		for (sd in streams) {
			try {
				if (sd.fileStream.deleteOnClose) {
					sd.fileStream.delete()
				} else {
					sd.fileStream.close()
				}
			} catch (_: Exception) {
			}
		}
		node.notifyTransferState()
	}

	override fun close() {
		// Close and remove all in-progress streams across all nodes
		for (node in nodeMaps.keys.toList()) {
			onNodeClosed(node)
		}
	}
}
