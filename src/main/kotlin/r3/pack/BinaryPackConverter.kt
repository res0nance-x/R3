package r3.pack

import r3.content.Content
import r3.io.BoundedInputStream
import r3.io.DEFAULT_COPY_BUFFER_SIZE
import r3.io.log
import r3.io.skipFullBytes
import r3.source.FileSink
import r3.source.FileSource
import r3.source.Source
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Reader for the legacy (pre-alpha-revision) BinaryPack format.
 */
class LegacyBinaryPack(val src: Source) : Pack {
	val contentList = ArrayList<Content>()

	init {
		src.createInputStream().buffered(DEFAULT_COPY_BUFFER_SIZE).let { istream ->
			DataInputStream(istream).use { stream ->
				var currentOffset = 0L
				try {
					while (true) {
						val nameLen: Int
						try {
							nameLen = stream.readInt()
						} catch (_: EOFException) {
							break
						}
						if (nameLen < 0 || nameLen > 10_000_000) break

						val nameBytes = ByteArray(nameLen)
						stream.readFully(nameBytes)
						val name = String(nameBytes)

						val typeLen = stream.readInt()
						if (typeLen < 0 || typeLen > 10_000_000) break

						val typeBytes = ByteArray(typeLen)
						stream.readFully(typeBytes)
						val type = String(typeBytes)

						val length = stream.readLong()
						val created = stream.readLong()
						if (length < 0) break

						val headerSize = 24L + nameBytes.size + typeBytes.size
						val pos = currentOffset + headerSize

						contentList.add(object : Content {
							override val path: String = name
							override val ext: String = type
							override val lastModified: Long = if (created > 0) created else 0L
							override val length: Long = length
							override fun createInputStream(): InputStream {
								val itemStream = src.createInputStream()
								itemStream.skipFullBytes(pos)
								return BoundedInputStream(itemStream, length)
							}

							override fun toString(): String = "$path $ext $length"
						})

						stream.skipFullBytes(length)
						currentOffset = pos + length
					}
				} catch (_: EOFException) {
					// Clean EOF
				} catch (e: Exception) {
					log(e)
				}
			}
		}
	}

	override fun iterator(): Iterator<Content> = contentList.iterator()
}

object BinaryPackConverter {
	/**
	 * Checks if a file is already in the new format by inspecting the first 4 magic bytes ("R3PK").
	 */
	fun isNewFormat(file: File): Boolean {
		if (!file.exists() || file.length() < 4) return false
		file.inputStream().use { input ->
			val buf = ByteArray(4)
			val read = input.read(buf)
			if (read < 4) return false
			return buf[0] == 0x52.toByte() &&
					buf[1] == 0x33.toByte() &&
					buf[2] == 0x50.toByte() &&
					buf[3] == 0x4B.toByte() // "R3PK"
		}
	}

	/**
	 * Converts a single pack file from legacy format to new format.
	 * Returns true if converted, false if skipped (already new format).
	 */
	fun convertFile(
		file: File,
		keepBackup: Boolean = false,
		progress: (Int) -> Unit = {}
	): Boolean {
		if (isNewFormat(file)) {
			return false
		}

		val legacyPack = LegacyBinaryPack(FileSource(file))
		val tempFile = File(file.parentFile, "${file.name}.tmp_${System.currentTimeMillis()}")

		try {
			BinaryPack.create(legacyPack, FileSink(tempFile), progress)

			// Verify newly created file before replacing original
			val verifyPack = BinaryPack(FileSource(tempFile))
			if (verifyPack.count() != legacyPack.count()) {
				throw IOException("Verification failed: entry count mismatch (old: ${legacyPack.count()}, new: ${verifyPack.count()})")
			}

			if (keepBackup) {
				val backupFile = File(file.parentFile, "${file.name}.old")
				Files.move(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
				Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
			} else {
				Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
			}
			return true
		} catch (e: Exception) {
			if (tempFile.exists()) tempFile.delete()
			throw e
		}
	}

	/**
	 * Finds and converts all pack files in the given directory.
	 */
	fun convertDirectory(
		dir: File,
		keepBackup: Boolean = false,
		onProgress: (file: File, fileIndex: Int, totalFiles: Int, percent: Int) -> Unit = { _, _, _, _ -> }
	) {
		require(dir.isDirectory) { "Target path is not a directory: ${dir.absolutePath}" }
		val packFiles = dir.listFiles { f -> f.isFile && f.name.endsWith(".pack", ignoreCase = true) }
			?.sortedBy { it.length() }
			?: return

		println("Found ${packFiles.size} pack files in ${dir.absolutePath}")
		packFiles.forEachIndexed { idx, file ->
			if (isNewFormat(file)) {
				println("[${idx + 1}/${packFiles.size}] Already new format: ${file.name}")
				onProgress(file, idx + 1, packFiles.size, 100)
			} else {
				println("[${idx + 1}/${packFiles.size}] Converting: ${file.name} (${file.length() / (1024 * 1024)} MB)...")
				var lastReported = -1
				val converted = convertFile(file, keepBackup) { pct ->
					if (pct != lastReported && pct % 10 == 0) {
						lastReported = pct
						onProgress(file, idx + 1, packFiles.size, pct)
						print("\r  Progress: $pct%")
					}
				}
				println()
				if (converted) {
					println("  -> Successfully converted: ${file.name}")
				}
			}
		}
	}
}

fun main(args: Array<String>) {
	val targetDir = if (args.isNotEmpty()) File(args[0]) else File("""E:\Downloads\""")
	println("Starting conversion of pack files in: ${targetDir.absolutePath}")
	BinaryPackConverter.convertDirectory(targetDir)
	println("All conversions complete!")
}
