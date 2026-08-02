package r3.thread

import java.util.concurrent.Executors

interface Chan<T> {
	fun send(obj: T)
}

class ChannelManager {
	private val executor = Executors.newCachedThreadPool {
		object : Thread() {
			override fun run() {
				it.run()
			}
		}.apply { isDaemon = true }
	}

	fun <T> connect(handle: (T) -> Unit): Chan<T> {
		return object : Chan<T> {
			override fun send(obj: T) {
				executor.execute {
					handle(obj)
				}
			}
		}
	}
}