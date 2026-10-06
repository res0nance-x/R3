package r3.io

var log: (String) -> Unit = { msg: String -> println(msg) }
var debug: (String) -> Unit = {}
var logError: (String, Throwable?) -> Unit = { msg: String, e: Throwable? ->
	if (e != null) {
		println("[ERROR] $msg: ${e.message}")
		e.printStackTrace(System.out)
	} else {
		println("[ERROR] $msg")
	}
}

fun log(msg: String, e: Throwable?) = logError(msg, e)
fun log(e: Throwable) = logError(e.message ?: "Exception", e)
