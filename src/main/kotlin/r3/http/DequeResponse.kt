package r3.http

import java.io.InputStream
import java.util.concurrent.BlockingDeque
import java.util.concurrent.TimeUnit

/**
 * An InputStream that reads from a BlockingDeque of ByteArray objects.
 * Blocks until data is available or the stream is closed by adding a null entry.
 */
class DequeInputStream(
	private val deque: BlockingDeque<ByteArray>,
	private val timeoutMillis: Long = 30000,
	private val finishedCallback: () -> Unit
) : InputStream() {
	private var currentArray: ByteArray? = null
	private var currentPosition = 0
	private var closed = false
	override fun read(): Int {
		if (closed) return -1

		while (true) {
			// If we have a current array, read from it
			currentArray?.let { arr ->
				if (currentPosition < arr.size) {
					return arr[currentPosition++].toInt() and 0xFF
				} else {
					// Finished with this array, get next
					currentArray = null
					currentPosition = 0
				}
			}
			// Need to get next array from deque
			val nextArray = deque.poll(timeoutMillis, TimeUnit.MILLISECONDS)
			if (nextArray == null || nextArray.isEmpty()) {
				// Timeout or stream closed
				closed = true
				finishedCallback()
				return -1
			}
			currentArray = nextArray
			currentPosition = 0
		}
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		if (closed) return -1
		if (len == 0) return 0
		var totalRead = 0
		var firstRead = true

		while (totalRead < len) {
			// If we have a current array, read from it
			val arr = currentArray
			if (arr != null && currentPosition < arr.size) {
				val available = arr.size - currentPosition
				val toRead = minOf(available, len - totalRead)
				System.arraycopy(arr, currentPosition, b, off + totalRead, toRead)
				currentPosition += toRead
				totalRead += toRead
				if (currentPosition >= arr.size) {
					currentArray = null
					currentPosition = 0
				}
				if (totalRead >= len) {
					// Buffer is full, return what we've read
					return totalRead
				}
				// Continue to read more if buffer not full
				firstRead = false
				continue
			}
			// Current array is exhausted or null, prepare for next
			currentArray = null
			currentPosition = 0
			// Need to get next array from deque
			// Block on first read, don't block if we've already read something
			val nextArray = if (firstRead) {
				deque.poll(timeoutMillis, TimeUnit.MILLISECONDS)
			} else {
				deque.poll()
			}

			when {
				nextArray == null -> {
					// Timeout or no more data immediately available
					if (totalRead == 0) {
						// Nothing read yet and timed out
						closed = true
						return -1
					} else {
						// Return what we have so far
						return totalRead
					}
				}

				nextArray.isEmpty() -> {
					// Empty array signals end of stream
					closed = true
					finishedCallback()
					return if (totalRead > 0) totalRead else -1
				}

				else -> {
					currentArray = nextArray
					currentPosition = 0
					firstRead = false
				}
			}
		}
		return 0
	}

	override fun available(): Int {
		return currentArray?.let { it.size - currentPosition } ?: 0
	}

	override fun close() {
		closed = true
		currentArray = null
	}
}