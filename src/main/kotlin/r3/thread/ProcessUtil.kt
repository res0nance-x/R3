package r3.thread

import java.io.File

fun List<String>.proc(): Process {
	val pb = ProcessBuilder(this)
	pb.redirectError(ProcessBuilder.Redirect.INHERIT)
	pb.directory(File("."))
	return pb.start()
}

fun List<String>.exec(): String {
	val pb = ProcessBuilder(this)
	pb.redirectError(ProcessBuilder.Redirect.INHERIT)
	pb.directory(File("."))
	val proc = pb.start()
	return String(proc.inputStream.readAllBytes())
}

fun Array<String>.proc(): Process {
	val pb = ProcessBuilder(this.toList())
	pb.redirectError(ProcessBuilder.Redirect.INHERIT)
	pb.directory(File("."))
	return pb.start()
}

fun Array<String>.exec(): String {
	val pb = ProcessBuilder(this.toList())
	pb.redirectError(ProcessBuilder.Redirect.INHERIT)
	pb.directory(File("."))
	val proc = pb.start()
	return String(proc.inputStream.readAllBytes())
}

fun printAllThreads() {
	val all = Thread.getAllStackTraces()
	for ((thread, stack) in all.entries) {
		System.out.printf(
			"Thread: \"%s\" tid=%d daemon=%b state=%s%n",
			thread.name, thread.id, thread.isDaemon, thread.state
		)
		for (e in stack) {
			println("\t" + e)
		}
		println()
	}
}