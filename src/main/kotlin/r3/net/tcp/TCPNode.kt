package r3.net.tcp

import r3.io.*
import r3.source.Source
import r3.thread.pthread
import r3.util.srnd
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingDeque

class TCPNode(
	private val socket: Socket,
	tempDir: File,
	contentHandler: (TCPNode, ByteArray, File?) -> Unit,
	maxConcurrentStreams: Int = 10,
	maxStreamSize: Long = 1024 * 1024 * 1024, // 1 GB
	streamTimeoutMs: Long = 30000 // 30 seconds
) : Closeable {
	private val outDeque = LinkedBlockingDeque<TCPBlock>(100)
	private val dis = DataInputStream(socket.getInputStream())
	private val dos = DataOutputStream(socket.getOutputStream())
	val remoteAddress = socket.remoteSocketAddress as InetSocketAddress
	private val blockHandler = TCPBlockHandler(
		tempDir,
		maxConcurrentStreams,
		maxStreamSize,
		streamTimeoutMs,
		contentHandler
	)

	@Volatile
	private var closeRequested = false

	/** Updated on every received block — used by the application-level ping scheduler. */
	@Volatile
	var lastActivityMs: Long = System.currentTimeMillis()
		private set

	/**
	 * Called exactly once when this node closes (remote disconnect, timeout, or explicit close).
	 * Cleared immediately before invocation so it can never fire twice.
	 */
	@Volatile
	var onClose: (() -> Unit)? = null

	init {
		pthread {
			try {
				while (!Thread.currentThread().isInterrupted) {
					val block = TCPBlock.read(dis)
					lastActivityMs = System.currentTimeMillis()
					blockHandler.handle(this, block)
				}
			} catch (e: Throwable) {
				if (!e.isNormalSocketClose(socket)) {
					log("TCPNode: $e for $this")
				}
			} finally {
				this.close()
			}
		}
		pthread {
			try {
				while (!Thread.currentThread().isInterrupted) {
					val block = outDeque.take()
					// ID can not be -1 normally, so use it as a signal to close
					if (block.id == -1L) {
						break
					}
					block.write(dos)
					if (outDeque.isEmpty()) {
						dos.flush()
					}
				}
			} catch (e: Throwable) {
				if (!e.isNormalSocketClose(socket)) {
					log("TCPNode: $e")
				}
			} finally {
				this.close()
			}
		}
	}

	fun send(header: ByteArray) {
		if (closeRequested) return
		val id = srnd.nextLong() and 0x7fffffffffffffff
		send(TCPBlock(id, header, true))
	}

	fun send(header: ByteArray, file: File) {
		if (closeRequested) return
		FileInputStream(file).buffered().use {
			send(header, it)
		}
	}

	fun send(header: ByteArray, arr: ByteArray) {
		if (closeRequested) return
		ByteArrayInputStream(arr).use {
			send(header, it)
		}
	}

	fun send(header: ByteArray, writable: Writable) {
		if (closeRequested) return
		send(header, writable.serialize())
	}

	fun send(header: ByteArray, source: Source) {
		if (closeRequested) return
		source.createInputStream().buffered().use {
			send(header, it)
		}
	}

	fun send(header: ByteArray, istream: InputStream) {
		if (closeRequested) return
		val id = srnd.nextLong() and 0x7fffffffffffffff
		val block = TCPBlock(id, header, false)
		send(block)
		val arr = ByteArray(16384)
		var read: Int
		var last = false
		while (!last) {
			read = istream.readMaxBytes(arr)
			last = read < arr.size
			send(TCPBlock(id, arr.copyOf(read), last))
		}
	}

	fun send(block: TCPBlock) {
		outDeque.put(block)
	}

	fun forceClose() {
		closeAll(dos, dis, socket, blockHandler)
	}

	override fun close() {
		if (closeRequested) return
		closeRequested = true
		val cb = onClose
		onClose = null
		try { cb?.invoke() } catch (_: Exception) { }
		// Signal the write thread to close after all queued blocks are sent
		var succeeded = outDeque.offer(TCPBlock(-1, ByteArray(0), true))
		var totalTime = 0
		while (totalTime < 1000 && !outDeque.isEmpty()) {
			if (!succeeded) {
				succeeded = outDeque.offer(TCPBlock(-1, ByteArray(0), true))
			}
			Thread.sleep(10)
			totalTime += 10
		}
		if (!outDeque.isEmpty()) {
			forceClose()
		}
	}

	fun isClosed(): Boolean {
		return closeRequested || socket.isClosed
	}

	override fun toString(): String {
		return "Node $remoteAddress"
	}
}