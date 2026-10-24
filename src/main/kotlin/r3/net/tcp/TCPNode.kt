package r3.net.tcp

import r3.io.*
import r3.source.Source
import r3.thread.pthread
import r3.util.srnd
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicInteger

class TCPNode(
	private val socket: Socket,
	tempDir: File,
	contentHandler: (TCPNode, ByteArray, File?) -> Unit,
	maxConcurrentStreams: Int = 10,
	maxStreamSize: Long = 1024 * 1024 * 1024, // 1 GB
	streamTimeoutMs: Long = 300000 // 5 minutes
) : Closeable {
	private val outDeque = LinkedBlockingDeque<TCPBlock>(100)
	private val dis = DataInputStream(BufferedInputStream(socket.getInputStream(), DEFAULT_COPY_BUFFER_SIZE))
	private val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), DEFAULT_COPY_BUFFER_SIZE))
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

	@Volatile
	var lastWriteMs: Long = System.currentTimeMillis()
		private set

	private val activeSendStreams = AtomicInteger(0)

	val activeSendStreamsCount: Int
		get() = activeSendStreams.get()

	val activeReceiveStreams: Int
		get() = blockHandler.activeStreamsCount

	fun isSending(): Boolean = activeSendStreams.get() > 0

	fun isReceiving(): Boolean = blockHandler.activeStreamsCount > 0

	fun isTransferring(): Boolean = isSending() || isReceiving()

	@Volatile
	var onTransferStateChanged: ((isTransferring: Boolean) -> Unit)? = null

	@Volatile
	var onStreamProgress: ((node: TCPNode, streamId: Long, header: ByteArray, bytesWritten: Long) -> Unit)? = null

	private fun notifyTransferState() {
		val transferring = isTransferring()
		try {
			onTransferStateChanged?.invoke(transferring)
		} catch (_: Exception) {}
	}

	/**
	 * Called exactly once when this node closes (remote disconnect, timeout, or explicit close).
	 * Cleared immediately before invocation so it can never fire twice.
	 */
	@Volatile
	var onClose: (() -> Unit)? = null

	init {
		try {
			socket.tcpNoDelay = true
			socket.keepAlive = true
		} catch (e: Exception) {
			log("TCPNode: Failed to set socket options: $e")
		}

		blockHandler.onStreamCountChanged = { notifyTransferState() }
		blockHandler.onStreamProgress = { streamId, header, bytesWritten ->
			onStreamProgress?.invoke(this, streamId, header, bytesWritten)
		}

		pthread {
			try {
				while (!Thread.currentThread().isInterrupted) {
					val block = TCPBlock.read(dis)
					lastActivityMs = System.currentTimeMillis()
					blockHandler.handle(this, block)
				}
			} catch (e: Throwable) {
				if (!e.isNormalSocketClose(socket)) {
					log("TCPNode readThread: $e for $this")
				}
			} finally {
				this.close()
			}
		}
		pthread {
			try {
				var batchStartTime = System.nanoTime()
				var batchBytes = 0L
				while (!Thread.currentThread().isInterrupted) {
					val block = outDeque.take()
					// ID can not be -1 normally, so use it as a signal to close
					if (block.id == -1L) {
						dos.flush()
						break
					}
					block.write(dos)
					lastWriteMs = System.currentTimeMillis()
					batchBytes += block.data.size
					if (outDeque.isEmpty() || block.last || batchBytes >= 4 * 1024 * 1024) {
						dos.flush()
						val elapsedMs = (System.nanoTime() - batchStartTime) / 1_000_000
						// Adaptive percentage delay: pause for 15% of transmission time of this 4MB batch.
						// Provides a clean 10-25ms airtime breather for Wi-Fi ACKs and MLO link rebalancing, tracking at ~87% of peak channel speed.
						if (batchBytes >= 1024 * 1024 && elapsedMs > 5) {
							val pauseMs = (elapsedMs * 0.15).toLong().coerceIn(5, 50)
							try {
								Thread.sleep(pauseMs)
							} catch (_: InterruptedException) {
								break
							}
						}
						batchStartTime = System.nanoTime()
						batchBytes = 0L
					}
				}
			} catch (e: Throwable) {
				if (!e.isNormalSocketClose(socket)) {
					log("TCPNode writeThread: $e for $this")
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

	fun sendPriority(header: ByteArray) {
		if (closeRequested) return
		val id = srnd.nextLong() and 0x7fffffffffffffff
		sendPriority(TCPBlock(id, header, true))
	}

	fun send(header: ByteArray, file: File) {
		if (closeRequested) return
		FileInputStream(file).buffered(DEFAULT_COPY_BUFFER_SIZE).use {
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
		source.createInputStream().buffered(DEFAULT_COPY_BUFFER_SIZE).use {
			send(header, it)
		}
	}

	fun send(header: ByteArray, istream: InputStream) {
		if (closeRequested) return
		activeSendStreams.incrementAndGet()
		notifyTransferState()
		try {
			val id = srnd.nextLong() and 0x7fffffffffffffff
			val block = TCPBlock(id, header, false)
			send(block)
			val arr = ByteArray(TCPBlock.MAXBLOCKSIZE)
			var read: Int
			var last = false
			while (!last) {
				if (closeRequested || socket.isClosed) break
				read = istream.readMaxBytes(arr)
				last = read < arr.size
				send(TCPBlock(id, arr.copyOf(read), last))
			}
		} finally {
			activeSendStreams.decrementAndGet()
			notifyTransferState()
		}
	}

	fun send(block: TCPBlock) {
		if (closeRequested || socket.isClosed) return
		while (!closeRequested && !socket.isClosed) {
			if (outDeque.offer(block, 200, java.util.concurrent.TimeUnit.MILLISECONDS)) {
				return
			}
		}
	}

	fun sendPriority(block: TCPBlock) {
		if (closeRequested || socket.isClosed) return
		while (!closeRequested && !socket.isClosed) {
			if (outDeque.offerFirst(block, 200, java.util.concurrent.TimeUnit.MILLISECONDS)) {
				return
			}
		}
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
		outDeque.clear()
		outDeque.offer(TCPBlock(-1, ByteArray(0), true))
		forceClose()
	}

	fun isClosed(): Boolean {
		return closeRequested || socket.isClosed
	}

	override fun toString(): String {
		return "Node $remoteAddress"
	}
}