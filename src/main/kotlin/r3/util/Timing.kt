package r3.util

var time = System.currentTimeMillis()
fun timeMark(label: String) {
	val delta = System.currentTimeMillis() - time
	println("$label $delta")
	time = System.currentTimeMillis()
}

fun measureTime(label: String, block: () -> Unit) {
	val time = System.currentTimeMillis()
	run(block)
	val delta = System.currentTimeMillis() - time
	println("$label $delta")
}