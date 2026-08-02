package r3.util

import java.util.*
import kotlin.math.min

// Android doesn't do Arrays.compare for ByteArray for older versions
fun compare(a: ByteArray, b: ByteArray): Int {
	val length = min(a.size, b.size)
	repeat(length) {
		val i = it
		if (a[i] != b[i]) {
			return a[i] - b[i]
		}
	}
	return a.size - b.size
}

fun <K, V> Map<K, V>.invert(): Map<V, K> {
	val orig = this
	return object : AbstractMap<V, K>() {
		override val entries: MutableSet<MutableMap.MutableEntry<V, K>>
			get() = orig.entries.let {
				val m = HashSet<MutableMap.MutableEntry<V, K>>()
				for (x in it) {
					m.add(object : MutableMap.MutableEntry<V, K> {
						override val key: V
							get() = x.value
						override val value: K
							get() = x.key

						override fun setValue(newValue: K): K {
							throw NotImplementedError()
						}
					})
				}
				m
			}
	}
}

fun <K, V> mapMergeAOverB(a: Map<K, V>, b: Map<K, V>): Map<K, V> {
	val map = LinkedHashMap<K, V>()
	map.putAll(b)
	map.putAll(a)
	return map
}