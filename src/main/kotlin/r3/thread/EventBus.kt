package r3.thread

import r3.io.log
import java.util.concurrent.CopyOnWriteArrayList

class EventBus<T> {
	private val listenArray = CopyOnWriteArrayList<Pair<(String) -> Boolean, (EventBus<T>, obj: T) -> Unit>>()
	fun addListen(matcher: (String) -> Boolean, f: (EventBus<T>, T) -> Unit) {
		listenArray.add(Pair(matcher, f))
	}

	operator fun set(key: String, obj: T) {
		var match = false
		for ((m, f) in listenArray) {
			if (m(key)) {
				match = true
				f(this, obj)
			}
		}
		if (!match) {
			log("dQTFJzFe2I: no match for key $key")
		}
	}
}