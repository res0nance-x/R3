package r3.pack

import r3.content.Content
import r3.io.log
import java.net.URLDecoder

/**
 * An indexed [Pack] adapter designed for MHTML and web resource collections with loose path mapping.
 *
 * MHTML resources saved from web browsers often feature inconsistent URL schemes, query strings,
 * percent-encoding differences, case discrepancies, or altered path hierarchies between the HTML
 * markup and the multipart archive entries.
 *
 * MHTPackMap resolves resources by:
 * 1. Looking at path only (stripping scheme, authority/domain, query parameters, and fragments).
 * 2. Exact match on clean path.
 * 3. Loose matching:
 *    - Ignoring percent-encoded characters (replacing them with a space or nothing, or URL-decoding).
 *    - Case-insensitive comparisons.
 *    - Truncating paths (matching path suffixes or prefixes).
 *    - Matching by filename only.
 *    - Matching by base filename (extension variations or missing extension).
 * 4. Outputting a warning whenever a loose (non-exact) match is made.
 * 5. Preserving the MIME type from the underlying MHTML entries.
 */
class MHTPackMap(
	private val list: List<Content>,
	val locationMap: Map<String, Content>? = null
) : PackMap(list) {
	constructor(iterable: Iterable<Content>) : this(iterable.toList(), null)
	constructor(iterable: Iterable<Content>, locations: Map<String, Content>?) : this(iterable.toList(), locations)

	// Exact lookup maps
	private val exactMap = HashMap<String, Content>()
	// Lowercase exact clean path -> Content
	private val lowerPathMap = HashMap<String, Content>()
	// Compact path (lowercase, percent-encodings and whitespace/separators removed) -> Content
	private val compactPathMap = HashMap<String, Content>()

	// Filename -> list of Content entries with that filename
	private val filenameMap = HashMap<String, MutableList<Content>>()
	// Lowercase filename -> list of Content entries
	private val lowerFilenameMap = HashMap<String, MutableList<Content>>()
	// Compact filename -> list of Content entries
	private val compactFilenameMap = HashMap<String, MutableList<Content>>()

	// Base name (without extension, lowercase) -> list of Content entries
	private val baseNameMap = HashMap<String, MutableList<Content>>()
	// Compact base name -> list of Content entries
	private val compactBaseNameMap = HashMap<String, MutableList<Content>>()

	init {
		for (item in list) {
			val path = item.path
			exactMap.putIfAbsent(path, item)
			val clean = cleanPath(path)
			if (clean.isNotEmpty()) {
				exactMap.putIfAbsent(clean, item)
				lowerPathMap.putIfAbsent(clean.lowercase(), item)
				compactPathMap.putIfAbsent(toCompact(clean), item)
			}

			// Pre-index percent-decoded and space-normalized variants
			val dec = runCatching { URLDecoder.decode(clean, Charsets.UTF_8.name()) }.getOrNull()
			if (dec != null && dec != clean) {
				exactMap.putIfAbsent(dec, item)
				lowerPathMap.putIfAbsent(dec.lowercase(), item)
				compactPathMap.putIfAbsent(toCompact(dec), item)
			}

			for (variant in generatePercentVariants(clean)) {
				exactMap.putIfAbsent(variant, item)
				lowerPathMap.putIfAbsent(variant.lowercase(), item)
				compactPathMap.putIfAbsent(toCompact(variant), item)
			}

			val fname = clean.substringAfterLast('/')
			if (fname.isNotEmpty()) {
				filenameMap.computeIfAbsent(fname) { ArrayList() }.add(item)
				lowerFilenameMap.computeIfAbsent(fname.lowercase()) { ArrayList() }.add(item)
				compactFilenameMap.computeIfAbsent(toCompact(fname)) { ArrayList() }.add(item)

				val base = fname.substringBeforeLast('.')
				if (base.isNotEmpty()) {
					baseNameMap.computeIfAbsent(base.lowercase()) { ArrayList() }.add(item)
					compactBaseNameMap.computeIfAbsent(toCompact(base)) { ArrayList() }.add(item)
				}
			}
		}
	}

	override operator fun get(path: String): Content? {
		if (path.isBlank()) {
			return exactMap["index.html"] ?: list.firstOrNull { it.path == "index.html" || it.ext == "html" }
		}

		// 1. Direct location match (e.g. original absolute URL or Content-ID if provided)
		locationMap?.get(path)?.let { return it }

		// 2. Clean the path to "path only" (strip scheme, host, query params, fragments, leading slashes)
		val clean = cleanPath(path)

		// 3. Exact match
		exactMap[clean]?.let { return it }
		exactMap[path]?.let { return it }

		if (clean.isEmpty() || clean == "index.html") {
			exactMap["index.html"]?.let { return it }
		}

		// 4. Loose matching: find closest match
		val (matched, reason) = findClosestMatch(path, clean)
		if (matched != null) {
			warnLooseMatch(path, matched, reason)
			return matched
		}

		return null
	}

	private fun findClosestMatch(rawPath: String, clean: String): Pair<Content?, String> {
		// A. Percent-encoding variations on full path
		val percentVars = generatePercentVariants(clean)
		for (variant in percentVars) {
			exactMap[variant]?.let { return Pair(it, "percent-encoding variant: '$variant'") }
		}

		// B. Case-insensitive match on full path (including percent variations)
		lowerPathMap[clean.lowercase()]?.let { return Pair(it, "case-insensitive match") }
		for (variant in percentVars) {
			lowerPathMap[variant.lowercase()]?.let { return Pair(it, "case-insensitive percent variant: '$variant'") }
		}

		// C. Compact match on full path (ignoring %-encodings, spaces, and casing)
		val compact = toCompact(clean)
		compactPathMap[compact]?.let { return Pair(it, "compact path match: '$compact'") }

		// D. Truncating paths (drop leading segments progressively from clean path)
		val segments = clean.split('/').filter { it.isNotEmpty() }
		if (segments.size > 1) {
			for (i in 1 until segments.size) {
				val subPath = segments.drop(i).joinToString("/")
				exactMap[subPath]?.let { return Pair(it, "truncated path match: '$subPath'") }
				lowerPathMap[subPath.lowercase()]?.let { return Pair(it, "truncated case-insensitive path match: '$subPath'") }
				compactPathMap[toCompact(subPath)]?.let { return Pair(it, "truncated compact path match: '$subPath'") }
			}
		}

		// Check if any entry path is a suffix of the requested path or vice-versa
		val cleanLower = clean.lowercase()
		for (item in list) {
			val itemClean = cleanPath(item.path).lowercase()
			if (itemClean.isNotEmpty() && (cleanLower.endsWith("/$itemClean") || itemClean.endsWith("/$cleanLower"))) {
				return Pair(item, "path suffix overlap: '${item.path}'")
			}
		}

		// E. Filename only match
		val fname = clean.substringAfterLast('/')
		if (fname.isNotEmpty()) {
			// Exact filename
			filenameMap[fname]?.let { candidates ->
				return Pair(pickBestCandidate(clean, candidates), "filename match: '$fname'")
			}
			// Filename percent variations
			for (variant in generatePercentVariants(fname)) {
				filenameMap[variant]?.let { candidates ->
					return Pair(pickBestCandidate(clean, candidates), "filename percent variant: '$variant'")
				}
			}
			// Case-insensitive filename
			lowerFilenameMap[fname.lowercase()]?.let { candidates ->
				return Pair(pickBestCandidate(clean, candidates), "case-insensitive filename: '$fname'")
			}
			for (variant in generatePercentVariants(fname)) {
				lowerFilenameMap[variant.lowercase()]?.let { candidates ->
					return Pair(pickBestCandidate(clean, candidates), "case-insensitive filename percent variant: '$variant'")
				}
			}
			// Compact filename (spaces and percent encodings removed)
			val compactFname = toCompact(fname)
			compactFilenameMap[compactFname]?.let { candidates ->
				return Pair(pickBestCandidate(clean, candidates), "compact filename: '$compactFname'")
			}

			// F. Base filename (ignoring extension or extension differences)
			val base = fname.substringBeforeLast('.')
			if (base.isNotEmpty()) {
				baseNameMap[base.lowercase()]?.let { candidates ->
					return Pair(pickBestCandidate(clean, candidates), "base-name match without extension: '$base'")
				}
				for (variant in generatePercentVariants(base)) {
					baseNameMap[variant.lowercase()]?.let { candidates ->
						return Pair(pickBestCandidate(clean, candidates), "base-name percent variant: '$variant'")
					}
				}
				val compactBase = toCompact(base)
				compactBaseNameMap[compactBase]?.let { candidates ->
					return Pair(pickBestCandidate(clean, candidates), "compact base-name match: '$compactBase'")
				}
			}
		}

		// G. CID or partial ID match
		if (rawPath.startsWith("cid:", ignoreCase = true) || rawPath.contains("@mhtml.blink", ignoreCase = true)) {
			val cleanId = rawPath.removePrefix("cid:").removePrefix("CID:")
				.removeSurrounding("<", ">")
				.substringBefore('@')
			for (item in list) {
				if (item.path.contains(cleanId, ignoreCase = true)) {
					return Pair(item, "Content-ID match: '$cleanId'")
				}
			}
		}

		return Pair(null, "")
	}

	/**
	 * When multiple entries share the same filename or base name, score candidates
	 * by common path segments from right to left to pick the closest match.
	 */
	private fun pickBestCandidate(requestedCleanPath: String, candidates: List<Content>): Content {
		if (candidates.size == 1) return candidates[0]

		val reqSegments = requestedCleanPath.split('/').filter { it.isNotEmpty() }.reversed()
		var bestCandidate = candidates[0]
		var bestScore = -1

		for (cand in candidates) {
			val candSegments = cand.path.split('/').filter { it.isNotEmpty() }.reversed()
			var score = 0
			val minLen = minOf(reqSegments.size, candSegments.size)
			for (i in 0 until minLen) {
				if (reqSegments[i].equals(candSegments[i], ignoreCase = true)) {
					score += 10
				} else if (reqSegments[i].substringBeforeLast('.').equals(candSegments[i].substringBeforeLast('.'), ignoreCase = true)) {
					score += 5
				} else {
					break
				}
			}
			if (score > bestScore) {
				bestScore = score
				bestCandidate = cand
			}
		}
		return bestCandidate
	}

	private fun warnLooseMatch(requested: String, matched: Content, reason: String) {
		log("MHTPackMap WARNING: Loose match for '$requested' -> '${matched.path}' ($reason)")
	}

	companion object {
		private val PERCENT_REGEX = Regex("""%[0-9a-fA-F]{2}""")

		/**
		 * Extracts the pure path component by stripping HTML quotes/entities, URI schemes,
		 * host/authority, query parameters, fragments, and leading slashes.
		 */
		fun cleanPath(raw: String): String {
			var p = raw.trim()
			if (p.startsWith("&quot;") && p.endsWith("&quot;")) {
				p = p.removeSurrounding("&quot;")
			}
			p = p.removeSurrounding("\"").removeSurrounding("'")
			if (p.isBlank() || p.startsWith("data:") || p.startsWith("blob:") || p.startsWith("javascript:")) {
				return ""
			}

			// CID reference
			if (p.startsWith("cid:", ignoreCase = true)) {
				val cid = p.substringAfter("cid:").substringAfter("CID:").removeSurrounding("<", ">")
				return cid.substringBefore('@')
			}

			// Strip scheme and host: "https://example.com/a/b" -> "a/b"
			if (p.contains("://")) {
				val afterScheme = p.substringAfter("://")
				p = afterScheme.substringAfter('/', "")
			} else if (p.startsWith("//")) {
				p = p.substring(2).substringAfter('/', "")
			}

			// Strip query parameters and fragment
			p = p.substringBefore('?').substringBefore('#')

			// Strip leading slashes
			return p.trimStart('/')
		}

		/**
		 * Compacts a path for loose matching: lowercase, and removing percent encodings,
		 * whitespace, and punctuation separators.
		 */
		fun toCompact(str: String): String {
			return PERCENT_REGEX.replace(str, "")
				.replace("+", "")
				.replace(" ", "")
				.replace("\t", "")
				.lowercase()
		}

		/**
		 * Generates path variations by ignoring percent-encoded characters:
		 * - Replacing %XX with space " "
		 * - Replacing %XX with nothing ""
		 * - Standard URL decoding
		 */
		fun generatePercentVariants(str: String): List<String> {
			val variants = LinkedHashSet<String>()

			// Replace space with %20 if present
			if (str.contains(' ')) {
				variants.add(str.replace(" ", "%20"))
				variants.add(str.replace(" ", ""))
			}

			if (str.contains('%') || str.contains('+')) {
				// 1. URL decoded
				runCatching { URLDecoder.decode(str, Charsets.UTF_8.name()) }.getOrNull()?.let {
					if (it != str) variants.add(it)
				}

				// 2. Replace %XX with space
				val withSpace = PERCENT_REGEX.replace(str, " ")
					.replace("+", " ")
					.replace(Regex("""\s+"""), " ")
					.trim()
				if (withSpace != str && withSpace.isNotEmpty()) {
					variants.add(withSpace)
				}

				// 3. Replace %XX with nothing
				val withNothing = PERCENT_REGEX.replace(str, "").replace("+", "")
				if (withNothing != str && withNothing.isNotEmpty()) {
					variants.add(withNothing)
				}
			}

			return variants.toList()
		}
	}
}
