package r3.pack

import r3.content.Content
import r3.content.ZipEntryContent
import r3.source.FileSource
import r3.source.Source
import java.io.Closeable
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

class ZipPack : Pack, Closeable, AutoCloseable {
	val file: File
	val stripCommonRoot: Boolean
	private val tempFile: File?
	private val zipFile: ZipFile
	private val list = ArrayList<Content>()
	private val map = HashMap<String, Content>()

	constructor(file: File, stripCommonRoot: Boolean = true) {
		this.file = file
		this.stripCommonRoot = stripCommonRoot
		this.tempFile = null
		this.zipFile = ZipFile(file)
		initEntries()
	}

	constructor(src: Source, stripCommonRoot: Boolean = true) {
		this.stripCommonRoot = stripCommonRoot
		if (src is FileSource) {
			this.file = src.file
			this.tempFile = null
		} else {
			val temp = File.createTempFile("zippack_", ".zip")
			temp.deleteOnExit()
			temp.outputStream().use { out ->
				src.createInputStream().use { inp -> inp.copyTo(out) }
			}
			this.file = temp
			this.tempFile = temp
		}
		this.zipFile = ZipFile(this.file)
		initEntries()
	}

	private fun initEntries() {
		val nonDirEntries = zipFile.entries().asSequence()
			.filter { !it.isDirectory && !it.name.endsWith("/") }
			.toList()

		val commonPrefix = if (stripCommonRoot && nonDirEntries.isNotEmpty()) {
			val firstNormalized = nonDirEntries.first().name.replace('\\', '/').removePrefix("/")
			val firstSlash = firstNormalized.indexOf('/')
			if (firstSlash > 0) {
				val rootDir = firstNormalized.substring(0, firstSlash + 1)
				if (nonDirEntries.all { it.name.replace('\\', '/').removePrefix("/").startsWith(rootDir) }) {
					rootDir
				} else ""
			} else ""
		} else ""

		for (x in nonDirEntries) {
			val rawPath = x.name.replace('\\', '/').removePrefix("/")
			val relPath = if (commonPrefix.isNotEmpty()) rawPath.removePrefix(commonPrefix) else rawPath
			if (relPath.isNotEmpty()) {
				val c = ZipEntryContent(zipFile, x, relPath)
				list.add(c)
				map[relPath] = c
			}
		}
	}

	val size: Int get() = list.size

	operator fun get(path: String): Content? {
		map[path]?.let { return it }
		val clean = path.removePrefix("/")
		map[clean]?.let { return it }

		val encoded = PackMap.encodePath(clean)
		if (encoded != clean) {
			map[encoded]?.let { return it }
		}

		val decoded = runCatching { URLDecoder.decode(clean, Charsets.UTF_8.name()) }.getOrNull()
		if (decoded != null && decoded != clean) {
			map[decoded]?.let { return it }
		}

		return null
	}

	override fun iterator(): Iterator<Content> {
		return list.iterator()
	}

	override fun close() {
		try {
			zipFile.close()
		} catch (_: Exception) {}
		tempFile?.delete()
	}
}