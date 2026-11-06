package r3.pack

import r3.content.Content
import r3.io.log

/**
 * An indexed [Pack] adapter designed for MHTML and web resource collections with loose path mapping.
 * Delegates to the unified [LoosePackMap] implementation.
 */
class MHTPackMap(
	list: List<Content>,
	locationMap: Map<String, Content>? = null
) : LoosePackMap(list, locationMap) {
	constructor(iterable: Iterable<Content>) : this(iterable.toList(), null)
	constructor(iterable: Iterable<Content>, locations: Map<String, Content>?) : this(iterable.toList(), locations)

	override fun warnLooseMatch(requested: String, matched: Content, reason: String) {
		log("MHTPackMap WARNING: Loose match for '$requested' -> '${matched.path}' ($reason)")
	}

	companion object {
		private val PERCENT_REGEX = Regex("""%[0-9a-fA-F]{2}""")

		fun cleanPath(raw: String): String {
			val segs = normalizeAndSegment(raw)
			return segs.joinToString("/")
		}

		fun toCompact(str: String): String {
			return PERCENT_REGEX.replace(str, "")
				.replace("+", "")
				.replace(" ", "")
				.replace("\t", "")
				.lowercase()
		}

		fun generatePercentVariants(str: String): List<String> {
			return listOf(str)
		}
	}
}
