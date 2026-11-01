package r3.net.tcp

import java.io.Closeable

interface BlockHandler : Closeable {
	fun handle(node: TCPNode, block: TCPBlock)
	fun activeStreamsCount(node: TCPNode): Int = 0
	fun onNodeClosed(node: TCPNode) {}
	override fun close() {}
}