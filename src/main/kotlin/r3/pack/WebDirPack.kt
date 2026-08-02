package r3.pack
//class WebDirPack(
//	val dir: File,
//	fileFilter: FileFilter = FileFilter { true },
//	dirFilter: FileFilter = FileFilter { true }
//) : MutablePack {
//	private val map = HashSimpleMap<Key256, Content>()
//
//	init {
//		dir.mkdirs()
//		val root = dir.toPath()
//		val visit = object : FileVisitor<Path> {
//			override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes?): FileVisitResult {
//				val f = dir.toFile()
//				return if (dirFilter.accept(f)) {
//					FileVisitResult.CONTINUE
//				} else {
//					FileVisitResult.SKIP_SUBTREE
//				}
//			}
//
//			override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
//				val f = file.toFile()
//				if (fileFilter.accept(f)) {
//					val content = FileContent(f, dir)
//					map[content.key] = content
//				}
//				return FileVisitResult.CONTINUE
//			}
//
//			override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
//				return FileVisitResult.CONTINUE
//			}
//
//			override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
//				return FileVisitResult.CONTINUE
//			}
//		}
//		Files.walkFileTree(root, visit)
//	}
//
//	// If we know this is a temporary file we can just move it instead
//	fun setMove(key: Key256, value: Content) {
//		if (value is FileContent) {
//			val dest = File(dir, value.name)
//			if (dest != value.file.absoluteFile) {
//				value.file.renameTo(dest)
//			}
//			map[key] = FileContent(dest, dir)
//		} else {
//			val f = File(dir, value.name)
//			FileOutputStream(f).use {
//				value.createInputStream().copyTo(it)
//			}
//			map[key] = FileContent(f, dir)
//		}
//	}
//
//	fun set(key: Key256, value: Content) {
//		val f = File(dir, value.name)
//		FileOutputStream(f).use {
//			value.createInputStream().copyTo(it)
//		}
//		map[key] = FileContent(f, dir)
//	}
//
//	fun remove(key: Key256) {
//		get(key)?.also {
//			File(dir, it.name).delete()
//		}
//	}
//
//	override fun add(content: Content) {
//		val file = File(dir, content.name)
//		file.outputStream().buffered().use { out ->
//			content.createInputStream().buffered().use {
//				it.copyTo(out)
//			}
//		}
//		map[content.key] = FileContent(file, dir)
//	}
//
//	override val size: Int
//		get() = map.size
//	override val keys: Set<Key256>
//		get() = map.keys
//
//	override operator fun get(key: Key256): Content? {
//		return map[key]
//	}
//
//	override fun visit(visitor: (Key256, Content) -> Unit) {
//		map.visit(visitor)
//	}
//
//	override fun toString(): String {
//		val sb = StringBuilder()
//		visit { k, c ->
//			sb.append(k.toString(), " ", c.toString())
//		}
//		return sb.toString()
//	}
//}