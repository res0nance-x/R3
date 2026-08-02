package r3.http

import r3.org.json.JSONArray
import r3.org.json.JSONObject
import r3.org.json.JSONTokener
import r3.collection.SimpleMap
import java.nio.file.Paths

class TemplateNotFound(message: String) : RuntimeException(message)
class IncludeCycleException(message: String) : RuntimeException(message)

/**
 * Tiny JSON-based template engine using org.json.* - Templates can embed JSON include-wrappers preceded by the marker '$.' (literal sequence `$.{` for includes).
 *   Example embedded in an HTML template:
 *     Some HTML... $.{"include":"templates/header.html","context":{"title":"This is the title"}} ...more HTML
 *     Hello, $.user.name! How are you today?
 *     $.{"include":"templates/footer.html", "user":{"name":"$.user.name"}}
 *   The engine will find such JSON objects, evaluate their context (resolving any $.refs against the parent context),
 *   render the included template and replace the JSON literal (including the marker) with the rendered result.
 * - Variables in text are referenced directly with the marker '$.' followed by a path (no braces required), e.g. $.title or $.user.name or $.fields[0].
 */
class SimpleTemplateEngine(private val templateMap: SimpleMap<String, String>) {
	fun render(templatePath: String, context: JSONObject): String {
		val warnings = mutableSetOf<String>()
		val result = renderInternal(templatePath, context, HashSet(), warnings)
		if (warnings.isNotEmpty()) {
			// Print warnings to stderr for visibility in logs
			System.err.println("Template engine warnings for '$templatePath':")
			for (w in warnings) System.err.println("  - $w")
			// Do not append warnings into the rendered output; return the result only
			return result
		}
		return result
	}

	private fun renderInternal(
		templatePath: String,
		context: JSONObject,
		seen: MutableSet<String>,
		warnings: MutableSet<String>
	): String {
		val path =
			Paths.get(if (templatePath.startsWith('/')) templatePath.substring(1) else templatePath).normalize().toString()
		if (!seen.add(path)) throw IncludeCycleException("Include cycle detected: $path")
		var current = if (path.startsWith('/')) {
			templateMap[path.substring(1)] ?: throw TemplateNotFound("Template not found: $templatePath")
		} else {
			templateMap[path] ?: throw TemplateNotFound("Template not found: $templatePath")
		}
		// Expand embedded include-wrappers marked by $.{ (literal sequence)
		val markerRegex = Regex("""\$\.\{""")
		var searchPos = 0
		while (true) {
			val m = markerRegex.find(current, searchPos) ?: break
			val markerStart = m.range.first
			// brace index points to the '{' included in the pattern
			val braceIndex = m.range.last
			// find end of JSON object starting at braceIndex
			val endIndex = findJsonObjectEnd(current, braceIndex)
			if (endIndex == -1) {
				// malformed JSON — warn and stop processing includes in this file
				val snippet = current.substring(braceIndex, kotlin.math.min(current.length, braceIndex + 200))
				warnings.add(
					"Malformed JSON include starting at offset $braceIndex in '$templatePath', snippet: ${
						snippet.replace(
							'\n',
							' '
						)
					}"
				)
				break
			}
			val jsonText = current.substring(braceIndex, endIndex)
			// parse the JSON object
			val parsedObj = try {
				val tok = JSONTokener(jsonText)
				val v = tok.nextValue()
				v as? JSONObject
			} catch (_: Exception) {
				null
			}

			if (parsedObj == null || !parsedObj.has("include")) {
				// not an include wrapper or missing include key — warn and skip this marker
				val snippet = jsonText.take(200).replace('\n', ' ')
				warnings.add("Found $.{...} in '$templatePath' that did not parse as an include wrapper (missing 'include' key or invalid JSON). Snippet: $snippet")
				searchPos = markerStart + 1
				continue
			}
			// Build included context by evaluating parsedObj.context against the current 'context'
			val includePath = parsedObj.getString("include")
			val contextValue = if (parsedObj.has("context")) parsedObj.get("context") else null
			val includedContext = buildIncludedContext(contextValue, context, warnings, templatePath)
			// Render the included template (recursively), then replace the marker+json with the rendered result
			val includedRendered = renderInternal(includePath, includedContext, seen, warnings)
			// replace from markerStart up to endIndex (endIndex exclusive)
			current = current.take(markerStart) + includedRendered + current.substring(endIndex)
			// continue loop to find more embedded include wrappers; reset searchPos to start after the inserted content
			searchPos = markerStart + includedRendered.length
		}
		// After expanding embedded include wrappers, substitute $.<path> variables across the resulting content
		val result = substituteVarsDirect(current, context, templatePath, warnings)

		seen.remove(path)
		return result
	}

	// Finds the index immediately after the matching `}` for a JSON object that starts at startIndex (which should point to '{').
	// Returns -1 if no matching end found.
	private fun findJsonObjectEnd(s: String, startIndex: Int): Int {
		var i = startIndex
		val len = s.length
		if (i >= len || s[i] != '{') return -1
		var depth = 0
		var inString = false
		while (i < len) {
			val c = s[i]
			if (inString) {
				if (c == '\\') {
					i += 2
					continue
				} else if (c == '"') {
					inString = false
				}
			} else {
				if (c == '"') {
					inString = true
				} else if (c == '{') {
					depth++
				} else if (c == '}') {
					depth--
					if (depth == 0) return i + 1
				}
			}
			i++
		}
		return -1
	}

	// Substitute direct-dollar markers like $.path.
	private fun substituteVarsDirect(
		content: String,
		context: JSONObject,
		templatePath: String,
		warnings: MutableSet<String>
	): String {
		// pattern: match only literal "$." followed by a path
		val varRegex = Regex("""\$\.\s*([a-zA-Z0-9_.\[\]\-]+)""")
		return varRegex.replace(content) { m ->
			val path = m.groupValues[1]
			val v = resolvePath(context, path)
			if (v == null || v === JSONObject.NULL) {
				warnings.add("Variable '\$.$path' not found in context when rendering '$templatePath'")
				""
			} else {
				when (v) {
					is JSONObject, is JSONArray -> v.toString()
					else -> v.toString()
				}
			}
		}
	}

	private fun buildIncludedContext(
		contextValue: Any?,
		parent: JSONObject,
		warnings: MutableSet<String>,
		templatePath: String
	): JSONObject {
		return when (contextValue) {
			null -> JSONObject()
			is JSONObject -> {
				val out = JSONObject()
				val keys = contextValue.keys()
				for (k in keys) {
					val v = contextValue.get(k)
					out.put(k, evaluateContextEntry(v, parent, warnings, templatePath))
				}
				out
			}

			is JSONArray -> JSONObject(
				mapOf(
					"array" to evaluateContextEntry(
						contextValue,
						parent,
						warnings,
						templatePath
					)
				)
			)

			is String -> {
				val ref = extractDollarPath(contextValue)
				if (ref != null) {
					val resolved = resolvePath(parent, ref)
					if (resolved == null) {
						warnings.add("Context reference '$.'$ref' in include wrapper for '$templatePath' resolved to null")
						JSONObject()
					} else resolved as? JSONObject ?: JSONObject(mapOf("value" to resolved))
				} else JSONObject(mapOf("value" to contextValue))
			}

			else -> JSONObject(mapOf("value" to contextValue))
		}
	}

	private fun extractDollarPath(s: String?): String? {
		if (s == null) return null
		val trimmed = s.trim()
		// accept only the literal "$." marker
		return if (trimmed.startsWith("$.")) trimmed.substring(2) else null
	}

	private fun evaluateContextEntry(
		value: Any?,
		parent: JSONObject,
		warnings: MutableSet<String>,
		templatePath: String
	): Any? {
		return when (value) {
			null -> JSONObject.NULL
			is String -> {
				val ref = extractDollarPath(value)
				if (ref != null) {
					val resolved = resolvePath(parent, ref)
					if (resolved == null) {
						warnings.add("Context reference '$.'$ref' in '$templatePath' resolved to null")
						JSONObject.NULL
					} else resolved
				} else value
			}

			is JSONObject -> {
				val out = JSONObject()
				val keys = value.keys()
				for (k in keys) {
					out.put(k, evaluateContextEntry(value.get(k), parent, warnings, templatePath))
				}
				out
			}

			is JSONArray -> {
				val arr = JSONArray()
				for (i in 0 until value.length()) arr.put(
					evaluateContextEntry(
						value.get(i),
						parent,
						warnings,
						templatePath
					)
				)
				arr
			}

			is Number, is Boolean -> value
			else -> value.toString()
		}
	}

	private fun resolvePath(rootObj: JSONObject, path: String): Any? {
		if (path.isEmpty()) return rootObj
		var current: Any? = rootObj
		var i = 0
		val len = path.length
		var token = StringBuilder()
		while (i < len) {
			val c = path[i]
			if (c == '.') {
				if (token.isNotEmpty()) {
					current = stepJson(current, token.toString())
					token = StringBuilder()
				}
				i++
			} else if (c == '[') {
				if (token.isNotEmpty()) {
					current = stepJson(current, token.toString())
					token = StringBuilder()
				}
				val end = path.indexOf(']', i)
				if (end == -1) return null
				val idxStr = path.substring(i + 1, end)
				val idx = idxStr.toIntOrNull() ?: return null
				current = stepIndex(current, idx)
				i = end + 1
			} else {
				token.append(c)
				i++
			}
		}
		if (token.isNotEmpty()) current = stepJson(current, token.toString())
		return current
	}

	private fun stepJson(current: Any?, key: String): Any? {
		return when (current) {
			is JSONObject -> if (current.has(key)) current.get(key) else null
			is Map<*, *> -> current[key]
			else -> null
		}
	}

	private fun stepIndex(current: Any?, index: Int): Any? {
		return when (current) {
			is JSONArray -> if (index in 0 until current.length()) current.get(index) else null
			is List<*> -> if (index in 0 until current.size) current[index] else null
			else -> null
		}
	}
}
