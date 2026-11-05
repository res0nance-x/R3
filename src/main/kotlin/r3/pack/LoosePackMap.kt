package r3.pack

import r3.content.Content
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

open class LoosePackMap(private val list: List<Content>) : PackMap(list) {

	private class Entry(
		val content: Content,
		val originalPath: String,
		val cleanPath: String,
		val segments: List<String>,
		val filename: String,
		val cleanPathLower: String,
		val filenameLower: String,
		val escapeUnderscoreLower: String,
		val escapeRemovedLower: String,
		val filenameEscapeUnderscoreLower: String,
		val filenameEscapeRemovedLower: String
	)

	private val entries: List<Entry>
	private val cleanExactMap: Map<String, Content>
	private val caseInsensitiveMap: Map<String, Content>
	private val escapeUnderscoreMap: Map<String, Content>
	private val escapeUnderscoreCollapsedMap: Map<String, Content>
	private val escapeRemovedMap: Map<String, Content>
	private val filenameMap: Map<String, MutableList<Entry>>
	private val filenameEscapeUnderscoreMap: Map<String, MutableList<Entry>>
	private val filenameEscapeRemovedMap: Map<String, MutableList<Entry>>

	private val cache = ConcurrentHashMap<String, Content>()
	private val negativeCache = ConcurrentHashMap.newKeySet<String>()

	init {
		val entryList = ArrayList<Entry>(list.size)
		val cExact = HashMap<String, Content>()
		val ciMap = HashMap<String, Content>()
		val euMap = HashMap<String, Content>()
		val eucMap = HashMap<String, Content>()
		val erMap = HashMap<String, Content>()
		val fnMap = HashMap<String, MutableList<Entry>>()
		val fnEuMap = HashMap<String, MutableList<Entry>>()
		val fnErMap = HashMap<String, MutableList<Entry>>()

		for (content in list) {
			val orig = content.path
			val clean = clean(orig)
			val segs = splitSegments(clean)
			val fn = segs.lastOrNull() ?: clean
			val cleanLower = clean.lowercase()
			val fnLower = fn.lowercase()
			val euLower = escapeToUnderscore(cleanLower)
			val eucLower = collapseUnderscores(euLower)
			val erLower = escapeRemoved(cleanLower)
			val fnEuLower = escapeToUnderscore(fnLower)
			val fnErLower = escapeRemoved(fnLower)

			val entry = Entry(
				content = content,
				originalPath = orig,
				cleanPath = clean,
				segments = segs,
				filename = fn,
				cleanPathLower = cleanLower,
				filenameLower = fnLower,
				escapeUnderscoreLower = euLower,
				escapeRemovedLower = erLower,
				filenameEscapeUnderscoreLower = fnEuLower,
				filenameEscapeRemovedLower = fnErLower
			)
			entryList.add(entry)

			cExact[clean] = content
			ciMap[cleanLower] = content
			euMap[euLower] = content
			eucMap[eucLower] = content
			erMap[erLower] = content

			fnMap.computeIfAbsent(fnLower) { ArrayList() }.add(entry)
			fnEuMap.computeIfAbsent(fnEuLower) { ArrayList() }.add(entry)
			fnErMap.computeIfAbsent(fnErLower) { ArrayList() }.add(entry)
		}

		entries = entryList
		cleanExactMap = cExact
		caseInsensitiveMap = ciMap
		escapeUnderscoreMap = euMap
		escapeUnderscoreCollapsedMap = eucMap
		escapeRemovedMap = erMap
		filenameMap = fnMap
		filenameEscapeUnderscoreMap = fnEuMap
		filenameEscapeRemovedMap = fnErMap
	}

	override operator fun get(path: String): Content? {
		return get(path, null)
	}

	fun get(path: String, rawPath: String?): Content? {
		// 1. Direct super check (fastest path)
		super.get(path)?.let { return it }
		if (rawPath != null) {
			super.get(rawPath)?.let { return it }
		}

		val cacheKey = if (rawPath != null && rawPath != path) "$path||$rawPath" else path
		if (negativeCache.contains(cacheKey)) return null
		cache[cacheKey]?.let { return it }

		val result = resolveLoose(path, rawPath)
		if (result != null) {
			cache[cacheKey] = result
		} else {
			negativeCache.add(cacheKey)
		}
		return result
	}

	private fun resolveLoose(path: String, rawPath: String?): Content? {
		val qPaths = listOfNotNull(path, rawPath).map { clean(it) }.distinct().filter { it.isNotEmpty() }
		if (qPaths.isEmpty()) return null

		// Stage 1: Exact clean path match
		for (q in qPaths) {
			cleanExactMap[q]?.let { return it }
		}

		// Stage 2: Case-insensitive full path match
		for (q in qPaths) {
			caseInsensitiveMap[q.lowercase()]?.let { return it }
		}

		// Stage 3: Escape-normalized full path match
		for (q in qPaths) {
			val qLower = q.lowercase()
			val eu = escapeToUnderscore(qLower)
			escapeUnderscoreMap[eu]?.let { return it }
			escapeUnderscoreCollapsedMap[collapseUnderscores(eu)]?.let { return it }
			val er = escapeRemoved(qLower)
			escapeRemovedMap[er]?.let { return it }
		}

		// Stage 4 & 5: Path-robust and simple filename match
		var bestMatch: ScoredMatch? = null

		for (q in qPaths) {
			val reqSegs = splitSegments(q)
			if (reqSegs.isEmpty()) continue
			val reqFn = reqSegs.last()
			val reqFnLower = reqFn.lowercase()
			val reqFnEuLower = escapeToUnderscore(reqFnLower)
			val reqFnErLower = escapeRemoved(reqFnLower)

			val candidateEntries = LinkedHashSet<Entry>()
			filenameMap[reqFnLower]?.let { candidateEntries.addAll(it) }
			filenameEscapeUnderscoreMap[reqFnEuLower]?.let { candidateEntries.addAll(it) }
			filenameEscapeUnderscoreMap[collapseUnderscores(reqFnEuLower)]?.let { candidateEntries.addAll(it) }
			filenameEscapeRemovedMap[reqFnErLower]?.let { candidateEntries.addAll(it) }

			for (entry in candidateEntries) {
				val candSegs = entry.segments
				val maxK = minOf(reqSegs.size, candSegs.size)
				var matchingSegs = 0
				var scoreSum = 0

				for (step in 1..maxK) {
					val rSeg = reqSegs[reqSegs.size - step]
					val cSeg = candSegs[candSegs.size - step]
					val segScore = matchSegment(rSeg, cSeg)
					if (segScore > 0) {
						matchingSegs++
						scoreSum += segScore
					} else {
						break
					}
				}

				if (matchingSegs > 0) {
					val isComplete = matchingSegs == reqSegs.size || matchingSegs == candSegs.size
					val scored = ScoredMatch(
						entry = entry,
						matchingSegments = matchingSegs,
						isCompleteSubpath = isComplete,
						segmentMatchScore = scoreSum,
						pathLengthDiff = abs(candSegs.size - reqSegs.size),
						totalCandSegments = candSegs.size,
						candPath = entry.cleanPath
					)
					if (bestMatch == null || scored < bestMatch) {
						bestMatch = scored
					}
				}
			}
		}

		return bestMatch?.entry?.content
	}

	private class ScoredMatch(
		val entry: Entry,
		val matchingSegments: Int,
		val isCompleteSubpath: Boolean,
		val segmentMatchScore: Int,
		val pathLengthDiff: Int,
		val totalCandSegments: Int,
		val candPath: String
	) : Comparable<ScoredMatch> {
		override fun compareTo(other: ScoredMatch): Int {
			// 1. More matching segments first (higher is better)
			if (this.matchingSegments != other.matchingSegments) {
				return other.matchingSegments.compareTo(this.matchingSegments)
			}
			// 2. Complete subpath match (higher is better)
			if (this.isCompleteSubpath != other.isCompleteSubpath) {
				return if (this.isCompleteSubpath) -1 else 1
			}
			// 3. Higher segment score first
			if (this.segmentMatchScore != other.segmentMatchScore) {
				return other.segmentMatchScore.compareTo(this.segmentMatchScore)
			}
			// 4. Smaller difference in path length first
			if (this.pathLengthDiff != other.pathLengthDiff) {
				return this.pathLengthDiff.compareTo(other.pathLengthDiff)
			}
			// 5. Shorter candidate path (fewer directories deep)
			if (this.totalCandSegments != other.totalCandSegments) {
				return this.totalCandSegments.compareTo(other.totalCandSegments)
			}
			// 6. Alphabetical for stability
			return this.candPath.compareTo(other.candPath)
		}
	}

	companion object {
		private val URL_ESCAPE_REGEX = Regex("%[0-9a-fA-F]{2}")
		private val PUNCT_TO_UNDERSCORE_REGEX = Regex("""[()\[\],!$'"]""")

		fun clean(p: String): String {
			val normalized = p.replace('\\', '/').trim('/')
			if (!normalized.contains('/')) {
				return if (normalized == "." || normalized == "..") "" else normalized
			}
			val parts = normalized.split('/')
			val resolved = mutableListOf<String>()
			for (part in parts) {
				if (part == "..") {
					if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
				} else if (part.isNotEmpty() && part != ".") {
					resolved.add(part)
				}
			}
			return resolved.joinToString("/")
		}

		fun splitSegments(cleanPath: String): List<String> {
			if (cleanPath.isEmpty()) return emptyList()
			return cleanPath.split('/').filter { it.isNotEmpty() }
		}

		fun escapeToUnderscore(s: String): String {
			return s.replace(URL_ESCAPE_REGEX, "_")
				.replace(PUNCT_TO_UNDERSCORE_REGEX, "_")
				.replace(' ', '_')
				.replace('+', '_')
		}

		fun collapseUnderscores(s: String): String {
			return s.replace(Regex("_+"), "_")
		}

		fun escapeRemoved(s: String): String {
			return s.replace(URL_ESCAPE_REGEX, "")
				.replace(PUNCT_TO_UNDERSCORE_REGEX, "")
				.replace(" ", "")
				.replace("+", "")
				.replace("_", "")
		}

		fun matchSegment(r: String, c: String): Int {
			if (r == c) return 5
			if (r.equals(c, ignoreCase = true)) return 4
			val rLower = r.lowercase()
			val cLower = c.lowercase()
			val rEu = escapeToUnderscore(rLower)
			val cEu = escapeToUnderscore(cLower)
			if (rEu == cEu) return 3
			if (collapseUnderscores(rEu) == collapseUnderscores(cEu)) return 2
			val rEr = escapeRemoved(rLower)
			val cEr = escapeRemoved(cLower)
			if (rEr == cEr) return 1
			return 0
		}
	}
}
