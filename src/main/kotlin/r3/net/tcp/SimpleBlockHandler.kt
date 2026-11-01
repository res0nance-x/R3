package r3.net.tcp

import r3.io.log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight in-memory [BlockHandler] for simple messaging or small streams
 * that do not require temporary files or custom file stream factories.
 */
class SimpleBlockHandler(
	private val maxConcurrentStreams: Int = 10,
	private val maxStreamSize: Int = 16 * 1024 * 1024, // 16 MB
	private val streamTimeoutMs: Long = 60_000, // 1 minute
	val contentHandler: (node: TCPNode, header: ByteArray, data: ByteArray?) -> Unit = { _, _, _ -> }
) : BlockHandler {
	private class MemoryStreamData(
      val header: ByteArray,
      val buffer: ByteArrayOutputStream = ByteArrayOutputStream(),
      var lastWrite: Long = System.currentTimeMillis()
	)

	private val nodeMaps = ConcurrentHashMap<TCPNode, HashMap<Long, MemoryStreamData>>()
	private val deadStreams = ConcurrentHashMap<TCPNode, MutableSet<Long>>()

	private fun streamsFor(node: TCPNode): HashMap<Long, MemoryStreamData> =
		nodeMaps.computeIfAbsent(node) { HashMap() }

	private fun deadStreamsFor(node: TCPNode): MutableSet<Long> =
		deadStreams.computeIfAbsent(node) { ConcurrentHashMap.newKeySet() }

	override fun activeStreamsCount(node: TCPNode): Int {
		val map = nodeMaps[node] ?: return 0
		return synchronized(map) { map.size }
	}

	private fun pruneStaleStreams(node: TCPNode) {
		val map = nodeMaps[node] ?: return
		val dead = deadStreamsFor(node)
		val now = System.currentTimeMillis()
		var prunedAny = false
		synchronized(map) {
			val iter = map.entries.iterator()
			while (iter.hasNext()) {
				val (id, sd) = iter.next()
				if (now - sd.lastWrite > streamTimeoutMs) {
					iter.remove()
					prunedAny = true
					if (dead.size > 200) dead.clear()
					dead.add(id)
               log("SimpleBlockHandler: pruning stale stream $id")
				}
			}
		}
		if (prunedAny) {
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
			val streamData = synchronized(map) { map[block.id] }
			if (streamData == null) {
				if (block.last) {
					contentHandler(node, block.data, null)
				} else {
					synchronized(map) {
						if (map.size >= maxConcurrentStreams) {
							throw IOException("Too many concurrent streams (max $maxConcurrentStreams)")
						}
						map[block.id] = MemoryStreamData(block.data)
					}
					node.notifyTransferState()
				}
			} else {
				if (streamData.buffer.size() + block.data.size > maxStreamSize) {
					dead.add(block.id)
					synchronized(map) { map.remove(block.id) }
					node.notifyTransferState()
					val err = IOException("Stream size limit exceeded ($maxStreamSize bytes)")
               log("SimpleBlockHandler: $err")
					node.close()
					throw err
				}
				streamData.buffer.write(block.data)
				streamData.lastWrite = System.currentTimeMillis()
				if (block.last) {
					synchronized(map) { map.remove(block.id) }
					node.notifyTransferState()
					contentHandler(node, streamData.header, streamData.buffer.toByteArray())
				}
			}
		} catch (e: Exception) {
         log("SimpleBlockHandler: $e")
			val map = nodeMaps[node]
			if (map != null) {
				val removed = synchronized(map) { map.remove(block.id) }
				if (removed != null) {
					node.notifyTransferState()
				}
			}
		}
	}

	override fun onNodeClosed(node: TCPNode) {
		deadStreams.remove(node)
		val map = nodeMaps.remove(node) ?: return
		synchronized(map) { map.clear() }
		node.notifyTransferState()
	}

	override fun close() {
		for (node in nodeMaps.keys.toList()) {
			onNodeClosed(node)
		}
	}
}