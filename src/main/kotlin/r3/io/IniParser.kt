package r3.io

import java.io.File

class IniParser(file: File) {
	private val sections = mutableMapOf<String, MutableMap<String, String>>()
	private var currentSection = ""

	init {
		sections[""] = mutableMapOf() // Default section for properties without a section
		file.forEachLine { line ->
			val trimmed = line.trim()
			// Skip empty lines and comments
			if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) {
				return@forEachLine
			}
			// Section header
			if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
				currentSection = trimmed.substring(1, trimmed.length - 1).trim()
				sections.putIfAbsent(currentSection, mutableMapOf())
				return@forEachLine
			}
			// Key-value pair
			val equalIndex = trimmed.indexOf('=')
			if (equalIndex > 0) {
				val key = trimmed.take(equalIndex).trim()
				val value = trimmed.substring(equalIndex + 1).trim()
					.removeSurrounding("\"") // Remove quotes if present
				sections[currentSection]?.put(key, value)
			}
		}
	}

	fun getString(key: String, section: String = ""): String? {
		return sections[section]?.get(key)
	}

	fun getString(key: String, section: String = "", default: String): String {
		return getString(key, section) ?: default
	}

	fun getInt(key: String, section: String = ""): Int? {
		return getString(key, section)?.toIntOrNull()
	}

	fun getInt(key: String, section: String = "", default: Int): Int {
		return getInt(key, section) ?: default
	}

	fun getBoolean(key: String, section: String = ""): Boolean? {
		return when (getString(key, section)?.lowercase()) {
			"true", "yes", "1", "on" -> true
			"false", "no", "0", "off" -> false
			else -> null
		}
	}

	fun getBoolean(key: String, section: String = "", default: Boolean): Boolean {
		return getBoolean(key, section) ?: default
	}

	fun getSection(section: String): Map<String, String>? {
		return sections[section]
	}

	fun getAllSections(): Set<String> {
		return sections.keys
	}

	fun has(key: String, section: String = ""): Boolean {
		return sections[section]?.containsKey(key) == true
	}

	override fun toString(): String {
		return buildString {
			sections.forEach { (section, properties) ->
				if (section.isNotEmpty()) {
					appendLine("[$section]")
				}
				properties.forEach { (key, value) ->
					appendLine("$key = $value")
				}
				if (properties.isNotEmpty()) {
					appendLine()
				}
			}
		}
	}
}