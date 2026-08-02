package r3.os

import r3.io.log

object Notification {
	var systemImplementation: (msg: String) -> Unit = { _: String ->
		log("Notification has not been implemented.")
	}

	fun notify(msg: String) {
		systemImplementation(msg)
	}
}