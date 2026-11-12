package r3.net.tcp

import java.net.InetSocketAddress
import java.nio.ByteBuffer

/**
 * Virtual [TCPNode] representing a peer-to-peer connection tunneled through a Bridge node.
 *
 * Outgoing blocks are wrapped with a 45-byte routing header:
 *   - Bytes 0..31:  32-byte target/source PeerKey
 *   - Bytes 32..35: 4-byte virtual port (Int, negative on the bridge)
 *   - Bytes 36..43: 8-byte inner TCPBlock.id (Long)
 *   - Byte 44:      1-byte inner TCPBlock.last (1 = true, 0 = false)
 *   - Bytes 45..N:  inner TCPBlock.data
 *
 * When the Bridge forwards a block from Peer A to Peer B, it overwrites bytes 0..31 with Peer A's
 * verified PeerKey and bytes 32..35 with Peer A's assigned virtual port on the Bridge, changing the
 * outer block ID from [TCPBlock.BRIDGE_TO_PEER_ID] (-2) to [TCPBlock.BRIDGE_FROM_PEER_ID] (-5)
 * (or -3 to -6 for priority blocks).
 */
class BridgeTCPNode(
	val carrierNode: TCPNode,
	val remotePeerKeyBytes: ByteArray,
	blockHandler: BlockHandler,
	virtualPort: Int
) : TCPNode(
	socket = null,
	blockHandler = blockHandler,
	remoteAddress = InetSocketAddress(carrierNode.remoteAddress.address, 0),
	remotePort = virtualPort
) {
	companion object {
		const val ROUTE_HEADER_SIZE = 32 + 4 + 8 + 1 // 45 bytes
		const val CLOSE_PAYLOAD_SIZE = 32 + 4        // 36 bytes

		fun wrapToPeerBlock(targetPeerKeyBytes: ByteArray, inner: TCPBlock, priority: Boolean): TCPBlock {
			require(targetPeerKeyBytes.size == 32) { "PeerKey must be 32 bytes" }
			val payload = ByteArray(ROUTE_HEADER_SIZE + inner.data.size)
			System.arraycopy(targetPeerKeyBytes, 0, payload, 0, 32)
			val buf = ByteBuffer.wrap(payload)
			buf.putInt(32, 0) // filled in by Bridge with sender's virtualPort
			buf.putLong(36, inner.id)
			payload[44] = if (inner.last) 1 else 0
			System.arraycopy(inner.data, 0, payload, ROUTE_HEADER_SIZE, inner.data.size)
			val outerId = if (priority) TCPBlock.BRIDGE_TO_PEER_PRIORITY_ID else TCPBlock.BRIDGE_TO_PEER_ID
			return TCPBlock(outerId, payload, true)
		}

		fun createCloseToPeerBlock(targetPeerKeyBytes: ByteArray): TCPBlock {
			require(targetPeerKeyBytes.size == 32) { "PeerKey must be 32 bytes" }
			val payload = ByteArray(CLOSE_PAYLOAD_SIZE)
			System.arraycopy(targetPeerKeyBytes, 0, payload, 0, 32)
			return TCPBlock(TCPBlock.BRIDGE_TO_PEER_CLOSE_ID, payload, true)
		}

		fun createCloseFromPeerBlock(sourcePeerKeyBytes: ByteArray, sourceVirtualPort: Int = 0): TCPBlock {
			require(sourcePeerKeyBytes.size == 32) { "PeerKey must be 32 bytes" }
			val payload = ByteArray(CLOSE_PAYLOAD_SIZE)
			System.arraycopy(sourcePeerKeyBytes, 0, payload, 0, 32)
			ByteBuffer.wrap(payload).putInt(32, sourceVirtualPort)
			return TCPBlock(TCPBlock.BRIDGE_FROM_PEER_CLOSE_ID, payload, true)
		}

		fun extractPeerKeyBytes(block: TCPBlock): ByteArray? {
			if (block.data.size < 32) return null
			return block.data.copyOfRange(0, 32)
		}

		fun extractVirtualPort(block: TCPBlock): Int {
			if (block.data.size < 36) return -1
			return ByteBuffer.wrap(block.data, 32, 4).int
		}

		/**
		 * Rewrites a Client -> Bridge block in-place into a Bridge -> Client block, stamping
		 * the authenticated sender's [sourcePeerKeyBytes] and [sourceVirtualPort].
		 */
		fun rewriteForRecipient(block: TCPBlock, sourcePeerKeyBytes: ByteArray, sourceVirtualPort: Int): TCPBlock? {
			if (block.data.size < 32 || sourcePeerKeyBytes.size != 32) return null
			val newId = when (block.id) {
				TCPBlock.BRIDGE_TO_PEER_ID -> TCPBlock.BRIDGE_FROM_PEER_ID
				TCPBlock.BRIDGE_TO_PEER_PRIORITY_ID -> TCPBlock.BRIDGE_FROM_PEER_PRIORITY_ID
				TCPBlock.BRIDGE_TO_PEER_CLOSE_ID -> TCPBlock.BRIDGE_FROM_PEER_CLOSE_ID
				else -> return null
			}
			val payload = if (block.data.size >= 36) {
				block.data
			} else {
				block.data.copyOf(36)
			}
			System.arraycopy(sourcePeerKeyBytes, 0, payload, 0, 32)
			ByteBuffer.wrap(payload, 32, 4).putInt(sourceVirtualPort)
			return TCPBlock(newId, payload, true)
		}

		fun unwrapInnerBlock(block: TCPBlock): TCPBlock? {
			if (block.data.size < ROUTE_HEADER_SIZE) return null
			val buf = ByteBuffer.wrap(block.data)
			val innerId = buf.getLong(36)
			val innerLast = block.data[44].toInt() != 0
			val innerData = block.data.copyOfRange(ROUTE_HEADER_SIZE, block.data.size)
			return TCPBlock(innerId, innerData, innerLast)
		}
	}

	override fun send(block: TCPBlock) {
		if (isClosed()) return
		lastWriteMs = System.currentTimeMillis()
		carrierNode.send(wrapToPeerBlock(remotePeerKeyBytes, block, priority = false))
	}

	override fun sendPriority(block: TCPBlock) {
		if (isClosed()) return
		lastWriteMs = System.currentTimeMillis()
		carrierNode.sendPriority(wrapToPeerBlock(remotePeerKeyBytes, block, priority = true))
	}

	fun receiveUnwrappedBlock(innerBlock: TCPBlock) {
		if (isClosed()) return
		lastActivityMs = System.currentTimeMillis()
		blockHandler.handle(this, innerBlock)
	}

	override fun forceClose() {
		blockHandler.onNodeClosed(this)
	}

	/**
	 * Closes this virtual node without sending a close notification back over the bridge
	 * (used when the remote peer or the bridge itself initiated the close).
	 */
	fun closeFromRemote() {
		if (closeRequested) return
		closeRequested = true
		val cb = onClose
		onClose = null
		try { cb?.invoke() } catch (_: Exception) {}
		forceClose()
	}

	override fun close() {
		if (closeRequested) return
		closeRequested = true
		if (!carrierNode.isClosed()) {
			try {
				carrierNode.sendPriority(createCloseToPeerBlock(remotePeerKeyBytes))
			} catch (_: Exception) {}
		}
		val cb = onClose
		onClose = null
		try { cb?.invoke() } catch (_: Exception) {}
		forceClose()
	}

	override fun isClosed(): Boolean {
		return closeRequested || carrierNode.isClosed()
	}

	override fun toString(): String {
		return "BridgeNode ${remoteAddress.address.hostAddress}:$remotePort"
	}
}
