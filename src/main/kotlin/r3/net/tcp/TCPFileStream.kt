package r3.net.tcp

import r3.io.log
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class TCPFileStream(
	val file: File,
	private val out: OutputStream,
	val deleteOnClose: Boolean = true,
	val onChunk: ((chunk: ByteArray) -> Unit)? = null
) : Closeable {
	constructor(
		file: File,
		append: Boolean = false,
		deleteOnClose: Boolean = true,
		onChunk: ((chunk: ByteArray) -> Unit)? = null
	) : this(
		file,
		FileOutputStream(file, append).buffered(r3.io.DEFAULT_COPY_BUFFER_SIZE),
		deleteOnClose,
		onChunk
	)

	private val queue = LinkedBlockingQueue<ByteArray>(256)
	@Volatile private var closed = false
	@Volatile private var writeError: Throwable? = null
	private val writerThread = kotlin.concurrent.thread(isDaemon = true, name = "FileStreamWriter") {
		try {
			while (!closed || queue.isNotEmpty()) {
				val chunk = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
				if (chunk.isEmpty()) break
				try {
					onChunk?.invoke(chunk)
				} catch (e: Throwable) {
					log("TCPFileStream onChunk error: $e")
				}
				out.write(chunk)
			}
		} catch (e: Throwable) {
			writeError = e
		} finally {
			try { out.close() } catch (_: Exception) {}
		}
	}

	fun write(data: ByteArray) {
		writeError?.let { throw it }
		if (!queue.offer(data, 30, TimeUnit.SECONDS)) {
			throw java.io.IOException("FileStream write queue full (storage stalled)")
		}
	}

	override fun close() {
		closed = true
		queue.offer(ByteArray(0))
		try {
			writerThread.join()
		} catch (_: Exception) {}
		writeError?.let { throw it }
	}

	fun delete() {
		closed = true
		queue.clear()
		queue.offer(ByteArray(0))
		try {
			writerThread.join(1000)
		} catch (_: Exception) {}
		try {
			out.close()
		} catch (_: Exception) {}
		try {
			if (file.exists()) {
				var deleted = false
				repeat(5) {
					if (!file.exists()) {
						deleted = true; return@repeat
					}
					if (file.delete()) {
						deleted = true; return@repeat
					}
					try { Thread.sleep(50) } catch (_: Exception) {}
				}
				if (!deleted) {
					try { file.deleteOnExit() } catch (_: Exception) {}
					log("TCPFileStream: unable to delete temp file after retries: $file")
				}
			}
		} catch (e: Exception) {
			log("TCPFileStream: exception deleting temp file $file: $e")
		}
	}
}
