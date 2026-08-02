package r3.app

import r3.key.Key256
import r3.util.Cache
import r3.util.LGMRandomSequence

class NameGen {
	val cache = Cache<Key256, String>(1000)

	companion object {
		private fun String.findPositions(): List<Int> {
			val positions = mutableListOf<Int>()
			var index = 0
			positions.add(0)
			while (index < length) {
				index = indexOf('\n', index)
				if (index == -1) break
				positions.add(index)
				index += 1
			}
			positions.add(length)
			return positions
		}

		val index = names.findPositions()
	}

	fun generateName(key: Key256): String {
		var name = cache[key]
		if (name == null) {
			val r = LGMRandomSequence(key.arr)
			val pos = (r.nextDouble() * (index.size - 2)).toInt()
			name = names.substring(index[pos] + 1, index[pos + 1])
			cache[key] = name
		}
		return name
	}
}