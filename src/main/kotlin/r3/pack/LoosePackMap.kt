package r3.pack

import r3.content.Content
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

private val DIACRITIC_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
private val PUNCT_REGEX = Regex("""[()\[\],!$'"]""")
private val UNDERSCORES_REGEX = Regex("_+")

fun normalizeAndSegment(rawPath: String): List<String> {
   if (rawPath.isBlank()) return emptyList()
   var p = rawPath.trim()
   if (p.startsWith("&quot;") && p.endsWith("&quot;")) {
      p = p.removeSurrounding("&quot;")
   }
   p = p.removeSurrounding("\"").removeSurrounding("'")
   if (p.startsWith("cid:", ignoreCase = true)) {
      p = p.substring(4).removeSurrounding("<", ">").substringBefore('@')
   } else if (p.contains("://")) {
      p = p.substringAfter("://").substringAfter('/', "")
   } else if (p.startsWith("//")) {
      p = p.substring(2).substringAfter('/', "")
   }
   val clean = p.substringBefore('?').substringBefore('#')
   if (clean.isBlank() || clean.startsWith("data:") || clean.startsWith("blob:") || clean.startsWith("javascript:")) {
      return emptyList()
   }

   // 1. URL decoding: protect '+' in path by replacing %20 specifically if needed,
   // then decode escapes safely.
   var decoded = clean.replace("+", "%2B")
   var previous = ""
   var passes = 0
   while (decoded != previous && passes < 3) {
      previous = decoded
      decoded = try {
         URLDecoder.decode(decoded, StandardCharsets.UTF_8.name())
      } catch (_: Exception) {
         break
      }
      passes++
   }
   // 2. Unicode normalization (NFKD) + strip diacritics
   val decomposed = Normalizer.normalize(decoded, Normalizer.Form.NFKD)
   val strippedUnicode = DIACRITIC_PATTERN.matcher(decomposed).replaceAll("")
   // 3. Lowercase & normalize spaces/escapes/punctuation to underscore
   val lowercased = strippedUnicode.lowercase()
   // 4. Split by '/' and '\', resolve '.' and '..'
   val rawSegments = lowercased.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }
   val resolved = mutableListOf<String>()
   for (seg in rawSegments) {
      if (seg == "..") {
         if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
      } else if (seg != ".") {
         // Treat spaces, escapes, and punctuation as underscores
         val normalizedSeg = seg.replace(PUNCT_REGEX, "_")
            .replace(' ', '_')
            .replace(UNDERSCORES_REGEX, "_")
         resolved.add(normalizedSeg)
      }
   }
   return resolved
}

fun isMatchingSegment(segA: String, segB: String): Boolean {
   if (segA == segB || segA.replace("_", "") == segB.replace("_", "")) return true
   // Allow equivalent web extensions: .html <-> .htm, .jpeg <-> .jpg
   val a = segA.replace("_", "")
   val b = segB.replace("_", "")
   val baseA = a.substringBeforeLast('.')
   val baseB = b.substringBeforeLast('.')
   val extA = a.substringAfterLast('.', "")
   val extB = b.substringAfterLast('.', "")
   if (baseA == baseB) {
      if ((extA == "html" && extB == "htm") || (extA == "htm" && extB == "html")) return true
      if ((extA == "jpeg" && extB == "jpg") || (extA == "jpg" && extB == "jpeg")) return true
   }
   return false
}

fun countMatchingSuffixSegments(a: List<String>, b: List<String>): Int {
   val minSize = minOf(a.size, b.size)
   var matches = 0
   for (i in 1..minSize) {
      val segA = a[a.size - i]
      val segB = b[b.size - i]
      if (isMatchingSegment(segA, segB)) {
         matches++
      } else {
         break
      }
   }
   return matches
}

open class LoosePackMap(
   private val list: List<Content>,
   val locationMap: Map<String, Content>? = null
) : PackMap(list) {
   constructor(iterable: Iterable<Content>) : this(iterable.toList(), null)
   constructor(iterable: Iterable<Content>, locationMap: Map<String, Content>?) : this(iterable.toList(), locationMap)

   val nmap: Map<List<String>, Content>
   private val leafIndex: Map<String, List<Pair<List<String>, Content>>>
   private val baseNameIndex: Map<String, List<Pair<List<String>, Content>>>
   private val cache = ConcurrentHashMap<String, Content>()
   private val negativeCache = ConcurrentHashMap.newKeySet<String>()

   init {
      val map = HashMap<List<String>, Content>()
      val leaves = HashMap<String, MutableList<Pair<List<String>, Content>>>()
      val baseNames = HashMap<String, MutableList<Pair<List<String>, Content>>>()

      for (content in list) {
         val segs = normalizeAndSegment(content.path)
         if (segs.isNotEmpty()) {
            map[segs] = content
            val stripped = segs.map { it.replace("_", "") }
            if (stripped != segs) {
               map.putIfAbsent(stripped, content)
            }

            val pair = segs to content
            val leaf = segs.lastOrNull()
            if (leaf != null) {
               leaves.computeIfAbsent(leaf) { ArrayList() }.add(pair)
               val strippedLeaf = leaf.replace("_", "")
               if (strippedLeaf != leaf) {
                  leaves.computeIfAbsent(strippedLeaf) { ArrayList() }.add(pair)
               }

               val base = leaf.substringBeforeLast('.')
               if (base.isNotEmpty()) {
                  baseNames.computeIfAbsent(base) { ArrayList() }.add(pair)
                  val strippedBase = base.replace("_", "")
                  if (strippedBase != base) {
                     baseNames.computeIfAbsent(strippedBase) { ArrayList() }.add(pair)
                  }
               }
            }
         }
      }

      nmap = map
      leafIndex = leaves
      baseNameIndex = baseNames
   }

   open fun warnLooseMatch(requested: String, matched: Content, reason: String) {}

   override operator fun get(path: String): Content? = get(path, null)

   fun get(path: String, rawPath: String? = null): Content? {
      if (path.trim('/', '\\').isBlank()) {
         return get("index.html", rawPath)
      }

      // 1. Direct location map lookup (e.g. original absolute URL or CID)
      locationMap?.get(path)?.let { return it }
      if (rawPath != null) {
         locationMap?.get(rawPath)?.let { return it }
      }

      // 2. Exact match on raw/passed path
      super.get(path)?.let { return it }
      if (rawPath != null) {
         super.get(rawPath)?.let { return it }
      }

      val cacheKey = if (rawPath != null && rawPath != path) "$path||$rawPath" else path
      if (negativeCache.contains(cacheKey)) return null
      cache[cacheKey]?.let { return it }

      val npath = normalizeAndSegment(path)
      var matchReason = "normalized path"
      val content = nmap[npath]
         ?: nmap[npath.map { it.replace("_", "") }]
         ?: run {
            val leaf = npath.lastOrNull()
            val leafStripped = leaf?.replace("_", "")
            val candidates = ArrayList<Pair<List<String>, Content>>()
            if (leaf != null) {
               leafIndex[leaf]?.let { candidates.addAll(it) }
               if (leafStripped != null && leafStripped != leaf) {
                  leafIndex[leafStripped]?.let { candidates.addAll(it) }
               }
               // Check equivalent web extensions (.html <-> .htm, .jpeg <-> .jpg)
               if (candidates.isEmpty()) {
                  val altLeaf = when {
                     leaf.endsWith(".html") -> leaf.removeSuffix("l")
                     leaf.endsWith(".htm") -> leaf + "l"
                     leaf.endsWith(".jpeg") -> leaf.substringBeforeLast('.') + ".jpg"
                     leaf.endsWith(".jpg") -> leaf.substringBeforeLast('.') + ".jpeg"
                     else -> null
                  }
                  if (altLeaf != null) {
                     leafIndex[altLeaf]?.let { candidates.addAll(it) }
                     val altLeafStripped = altLeaf.replace("_", "")
                     if (altLeafStripped != altLeaf) {
                        leafIndex[altLeafStripped]?.let { candidates.addAll(it) }
                     }
                  }
               }
               // Base name match without extension (e.g. cid:css-12345 or extensionless link)
               if (candidates.isEmpty() && (!leaf.contains('.') || path.contains("cid:", ignoreCase = true) || path.contains("@mhtml.blink", ignoreCase = true))) {
                  val base = leaf.substringBeforeLast('.')
                  baseNameIndex[base]?.let { candidates.addAll(it) }
                  val baseStripped = base.replace("_", "")
                  if (baseStripped != base) {
                     baseNameIndex[baseStripped]?.let { candidates.addAll(it) }
                  }
               }
               // If index.html requested and not found, check common defaults or sole HTML file
               if (candidates.isEmpty() && (leaf == "index.html" || leaf == "index.htm")) {
                  for (defaultName in listOf("default.html", "default.htm", "main.html", "main.htm", "home.html", "home.htm")) {
                     leafIndex[defaultName]?.let { candidates.addAll(it) }
                     if (candidates.isNotEmpty()) break
                  }
                  if (candidates.isEmpty()) {
                     val htmlFiles = list.filter { it.ext.equals("html", ignoreCase = true) || it.ext.equals("htm", ignoreCase = true) }
                     if (htmlFiles.size == 1) {
                        val singleHtml = htmlFiles[0]
                        val segs = normalizeAndSegment(singleHtml.path)
                        candidates.add(segs to singleHtml)
                     }
                  }
               }
            }

            // Pick candidate with the highest matching suffix segments
            var bestScore = 0
            var bestContent: Content? = null
            var bestLengthDiff = Int.MAX_VALUE

            for ((candidatePath, candContent) in candidates) {
               val score = countMatchingSuffixSegments(candidatePath, npath)
               if (score > bestScore) {
                  bestScore = score
                  bestContent = candContent
                  bestLengthDiff = kotlin.math.abs(candidatePath.size - npath.size)
               } else if (score == bestScore && score > 0) {
                  val diff = kotlin.math.abs(candidatePath.size - npath.size)
                  if (diff < bestLengthDiff) {
                     bestContent = candContent
                     bestLengthDiff = diff
                  }
               }
            }
            if (bestContent == null && candidates.isNotEmpty() && (leaf == "index.html" || leaf == "index.htm" || (leaf != null && !leaf.contains('.')) || path.contains("cid:", ignoreCase = true))) {
               bestContent = candidates[0].second
            }
            if (bestContent != null) {
               matchReason = if (bestScore > 0) "suffix match score=$bestScore" else "loose candidate match"
            }
            bestContent
         }

      if (content != null) {
         cache[cacheKey] = content
         warnLooseMatch(path, content, matchReason)
      } else {
         negativeCache.add(cacheKey)
      }
      return content
   }
}
