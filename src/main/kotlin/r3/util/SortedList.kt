package r3.util

import java.util.*

class ComparableComparator<T : Comparable<T>> : Comparator<T> {
	override fun compare(a: T, b: T): Int {
		return a.compareTo(b)
	}
}

data class KeyValue<K, V>(val key: K, val value: V) {
	override fun toString(): String {
		return "($key, $value)"
	}
}

@Suppress("UNCHECKED_CAST")
class SortedList<K, V>(val emptyKey: K, val minValue: V, val compare: (a: KeyValue<K, V>, b: KeyValue<K, V>) -> Int) :
	Iterable<KeyValue<K, V>> {
	val default = KeyValue(emptyKey, minValue)
	val c = Comparator<KeyValue<K, V>> { a, b -> compare(a, b) }
	var arr: Array<KeyValue<K, V>> = Array(4) { default }
	var size = 0
	fun add(item: KeyValue<K, V>) {
		val index = indexOf(item).let {
			if (it > -1) {
				it
			} else {
				-1 - it
			}
		}
		if (size == arr.size - 1) {
			val nkarr = Array(arr.size * 2) { default }
			System.arraycopy(arr, 0, nkarr, 0, size)
			arr = nkarr
		}
		System.arraycopy(arr, 0, arr, 0, index)
		System.arraycopy(arr, index, arr, index + 1, size - index)
		arr[index] = item
		++size
	}

	fun add(kvarr: Array<KeyValue<K, V>>) {
		kvarr.sortWith(c)
		arr = merge(kvarr, kvarr.size, arr, size, c)
		size += kvarr.size
	}

	fun add(kvList: List<KeyValue<K, V>>) {
		add(kvList.toMutableList().toTypedArray())
	}

	fun get(index: Int): KeyValue<K, V> {
		return arr[index]
	}

	fun get(key: K): List<KeyValue<K, V>> {
		var index = indexOf(KeyValue(key, minValue))
		val list = ArrayList<KeyValue<K, V>>()
		if (index < 0) {
			return list
		}
		while ((index - 1).let { it > 0 && arr[it].key == key }) {
			--index
		}
		while (index < arr.size && arr[index].key == key) {
			list.add(arr[index])
			++index
		}
		return list
	}

	fun remove(index: Int) {
		System.arraycopy(arr, 0, arr, 0, index + 1)
		System.arraycopy(arr, index + 1, arr, index, size - index - 1)
		arr[size - 1] = default
		--size
	}

	fun removeRange(range: IntRange) {
		System.arraycopy(arr, 0, arr, 0, range.first - 1)
		System.arraycopy(arr, range.last, arr, range.first - 1, size - range.last - 1)
		size -= range.last - range.first + 1
	}

	fun removeBatch(indexList: List<Int>) {
		for (i in indexList) {
			arr[i] = default
		}
	}

	fun indexOf(key: KeyValue<K, V>): Int {
		var index = Arrays.binarySearch(arr, 0, size, key, c)
		while ((index - 1).let { it > 0 && arr[it].key == key }) {
			--index
		}
		return index
	}

	override fun iterator(): Iterator<KeyValue<K, V>> {
		return object : Iterator<KeyValue<K, V>> {
			var pos = 0
			override fun hasNext(): Boolean {
				return pos < arr.size
			}

			override fun next(): KeyValue<K, V> {
				return arr[pos++]
			}
		}
	}
}

@Suppress("UNCHECKED_CAST")
fun <T> merge(a: Array<T>, asize: Int, b: Array<T>, bsize: Int, c: Comparator<T>): Array<T> {
	val arr = Array<Any>(a.size + b.size) {} as Array<T>
	var i = 0
	var j = 0
	var k = 0
	while (i < asize && j < bsize) {
		if (c.compare(a[i], b[j]) < 0) {
			arr[k++] = a[i++]
		} else {
			arr[k++] = b[j++]
		}
	}
	while (i < asize) {
		arr[k++] = a[i++]
	}
	while (j < bsize) {
		arr[k++] = b[j++]
	}
	return arr
}