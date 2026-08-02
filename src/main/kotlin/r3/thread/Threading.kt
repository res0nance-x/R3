package r3.thread

import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import kotlin.concurrent.thread
import kotlin.reflect.KProperty

val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
	Thread(runnable).apply {
		isDaemon = true
	}
}
fun pthread(block: () -> Unit): Future<*> {
	return executor.submit(block)
}

fun pthread(runnable: Runnable): Future<*> {
	return executor.submit(runnable)
}

fun <T> async(work: () -> T): CompletableFuture<T> {
	return CompletableFuture.supplyAsync(Supplier<T> { work() }, executor)
}

class FutureDelegate<T>(private val future: Future<T>) {
	operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
		return future.get()
	}
}

class ParallelLoadDelegate<T>(factory: () -> T) {
	val fut = executor.submit(factory)
	operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
		return fut.get()
	}
}

fun <X, Y> threadProcessor(incoming: Iterator<X>, f: (X) -> Y, handleResult: (Y) -> Unit = {}, done: () -> Unit = {}) {
	fun getNextIncoming(): X? {
		return synchronized(incoming) {
			if (incoming.hasNext()) {
				incoming.next()
			} else {
				null
			}
		}
	}

	thread(isDaemon = true) {
		var x = getNextIncoming()
		while (x != null) {
			handleResult(f(x))
			x = getNextIncoming()
		}
		done()
	}
}

fun <X, Y> parallelProcess(incoming: Iterator<X>, f: (X) -> Y, nThreads: Int = 16): Iterator<Y> {
	val synch = LinkedBlockingDeque<Y>(1)
	val activeCount = AtomicInteger(nThreads)
	repeat(nThreads) {
		threadProcessor(incoming, f, { synch.put(it) }, { activeCount.decrementAndGet() })
	}
	return object : Iterator<Y> {
		override fun hasNext(): Boolean {
			return synch.isNotEmpty() || activeCount.get() > 0
		}

		override fun next(): Y {
			return synch.take()
		}
	}
}

class ReOrder<T:Any>(private val iter: Iterator<T>, comparator: Comparator<T>? = null, queueSize: Int = 128) : Iterator<T> {
	private val queue = if (comparator == null) {
		PriorityQueue()
	} else {
		PriorityQueue<T>(comparator)
	}

	init {
		repeat(queueSize) {
			if (iter.hasNext()) {
				queue.add(iter.next())
			}
		}
	}

	override fun hasNext(): Boolean {
		return queue.isNotEmpty()
	}

	override fun next(): T {
		val cur = queue.remove()
		if (iter.hasNext()) {
			queue.add(iter.next())
		}
		return cur
	}
}