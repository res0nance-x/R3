package r3.io

import r3.content.Content
import r3.content.FileContent
import r3.content.ZipEntryContent
import java.io.*
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingDeque
import java.util.zip.ZipFile
import kotlin.io.path.fileSize

// ReadNBytes doesn't exist for older Android, so we do it ourselves.
fun InputStream.readFixedBytes(length: Int): ByteArray {
	val arr = ByteArray(length)
	var tot = 0
	while (tot < length) {
		val read = read(arr, tot, length - tot)
		if (read < 0) {
			throw EOFException()
		}
		tot += read
	}
	return arr
}

// ReadNBytes doesn't exist for older Android, so we do it ourselves.
fun InputStream.readFixedBytes(b: ByteArray, off: Int, len: Int): Int {
	var n = 0
	while (n < len) {
		val count: Int = read(b, off + n, len - n)
		if (count < 0) {
			break
		}
		n += count
	}
	return n
}

// Try to fill the array. If we hit EOF then return how many we did read
// Will return 0 if EOF
fun InputStream.readMaxBytes(arr: ByteArray, max: Int = arr.size): Int {
	var tot = 0
	while (tot < max) {
		val read = read(arr, tot, max - tot)
		if (read < 0) {
			break
		}
		tot += read
	}
	return tot
}

fun InputStream.readFully(b: ByteArray): Int {
	var n = 0
	val len = b.size
	while (n < len) {
		val read: Int = read(b, n, len - n)
		if (read < 0) {
			break
		}
		n += read
	}
	return n
}

fun getFiles(
	dir: File, acceptFile: FileFilter = FileFilter { true },
	acceptDir: FileFilter = FileFilter { true }, list: MutableList<File>? = null
): List<File> {
	val recurList = list ?: ArrayList()
	val fileList = dir.listFiles()?.toList() ?: ArrayList<File>()
	for (x in fileList) {
		if (x.isDirectory && acceptDir.accept(x)) {
			getFiles(x, acceptFile, acceptDir, recurList)
		} else if (x.isFile && acceptFile.accept(x)) {
			recurList.add(x)
		}
	}
	return recurList
}

fun Path.sumOfFiles(): Long {
	var size = 0L
	val fileList = Files.list(this)
	for (x in fileList) {
		if (Files.isDirectory(x)) {
			size += x.sumOfFiles()
		} else
			size += x.fileSize()
	}
	return size
}

fun File.sumOfFiles(): Long {
	var size = 0L
	val fileList = listFiles()?.toList() ?: ArrayList<File>()
	for (x in fileList) {
		if (x.isDirectory) {
			size += x.sumOfFiles()
		} else if (x.isFile) {
			size += x.length()
		}
	}
	return size
}

fun File.toDataInputStream(): DataInputStream {
	return DataInputStream(FileInputStream(this).buffered())
}

fun File.toDataOutputStream(): DataOutputStream {
	return DataOutputStream(FileOutputStream(this).buffered())
}

fun File.getNameAndExtension(): Pair<String, String> {
	return getNameAndExtension(name)
}

fun getNameAndExtension(name: String): Pair<String, String> {
	val lastIndex = name.lastIndexOf('.')
	return if (lastIndex > 0 && lastIndex != (name.length - 1)) {
		Pair(name.substring(0, lastIndex), name.substring(lastIndex + 1))
	} else {
		Pair(name, "")
	}
}

fun getExtension(filename: String): String {
	val index = filename.lastIndexOf('.')
	return if (index == -1) {
		""
	} else {
		filename.substring(index + 1)
	}
}

fun Short.serialize(): ByteArray {
	return ByteArray(2) { i -> ((this.toInt() ushr ((1 - i) * 8)) and 0xFF).toByte() }
}

fun Int.serialize(): ByteArray {
	return ByteArray(4) { i -> ((this ushr ((3 - i) * 8)) and 0xFF).toByte() }
}

fun Long.serialize(): ByteArray {
	return ByteArray(8) { i -> ((this ushr ((7 - i) * 8)) and 0xFF).toByte() }
}

fun ByteArray.toShort(): Short {
	return (((this[0].toInt() and 0xFF) shl 8) or (this[1].toInt() and 0xFF)).toShort()
}

fun ByteArray.toInt(): Int {
	return ((this[0].toInt() and 0xFF) shl 24) or ((this[1].toInt() and 0xFF) shl 16) or
			((this[2].toInt() and 0xFF) shl 8) or (this[3].toInt() and 0xFF)
}

fun ByteArray.toLong(): Long {
	return ((this[0].toLong() and 0xFF) shl 56) or ((this[1].toLong() and 0xFF) shl 48) or
			((this[2].toLong() and 0xFF) shl 40) or ((this[3].toLong() and 0xFF) shl 32) or
			((this[4].toLong() and 0xFF) shl 24) or ((this[5].toLong() and 0xFF) shl 16) or
			((this[6].toLong() and 0xFF) shl 8) or (this[7].toLong() and 0xFF)
}

fun InputStream.readAll(): ByteArray {
	val out = ByteArrayOutputStream()
	val buf = ByteArray(8192)
	var read = read(buf)
	while (read > -1) {
		out.write(buf, 0, read)
		read = read(buf)
	}
	return out.toByteArray()
}

fun InputStream.skipFullBytes(n: Long) {
	var skp = n
	if (skp > 0) {
		val ns: Long = skip(skp)
		if (ns in 0 until skp) { // skipped too few bytes
			// adjust number to skip
			skp -= ns
			// read until requested number skipped or EOS reached
			while (skp > 0 && read() != -1) {
				skp--
			}
			// if not enough skipped, then EOFE
			if (skp != 0L) {
				throw EOFException()
			}
		} else if (ns != skp) { // skipped negative or too many bytes
			throw IOException("Unable to skip exactly")
		}
	}
}

fun RandomAccessFile.skipNBytes(n: Long) {
	val length = length()
	val position = filePointer
	if (position + n > length) {
		throw EOFException()
	}
	seek(position + n)
}

fun RandomAccessFile.toOutputStream(): OutputStream {
	val raf = this
	return object : OutputStream() {
		override fun write(b: Int) {
			raf.write(b)
		}

		override fun write(b: ByteArray, off: Int, len: Int) {
			raf.write(b, off, len)
		}

		override fun close() {
			raf.close()
		}
	}
}

fun RandomAccessFile.toInputStream(allowClose: Boolean = false): InputStream {
	val raf = this
	return object : InputStream() {
		override fun read(): Int {
			return raf.read()
		}

		override fun read(b: ByteArray, off: Int, len: Int): Int {
			return raf.read(b, off, len)
		}

		override fun available(): Int {
			val avail = raf.length() - raf.filePointer
			return if (avail > Integer.MAX_VALUE) {
				Integer.MAX_VALUE
			} else {
				avail.toInt()
			}
		}

		override fun skip(n: Long): Long {
			val length = raf.length()
			val cur = raf.filePointer
			val skipLength = if (cur + n < length) {
				n
			} else {
				length - cur
			}
			raf.seek(cur + skipLength)
			return skipLength
		}

		override fun close() {
			if (allowClose) {
				raf.close()
			}
		}
	}
}

fun BigInteger.write(dos: DataOutputStream) {
	val ba = this.toByteArray()
	dos.writeShort(ba.size)
	dos.write(ba)
}

fun readBigInteger(dis: DataInputStream): BigInteger {
	val len = dis.readUnsignedShort()
	val ba = ByteArray(len)
	dis.readFully(ba)
	return BigInteger(1, ba)
}

fun ByteArray.toDataInputStream(): DataInputStream {
	return DataInputStream(ByteArrayInputStream(this))
}

// Unused, but maybe useful for something else one day
class ByteBlockInputStream : InputStream() {
	private val deque = LinkedBlockingDeque<ByteArray>(10)
	private var bais = ByteArrayInputStream(deque.takeFirst())
	private var close = false
	override fun read(): Int {
		if (close) {
			return -1
		}
		var x = bais.read()
		if (x < 0) {
			bais = try {
				ByteArrayInputStream(deque.takeFirst())
			} catch (e: InterruptedIOException) {
				log("d9ZgGOa7Va: $e")
				close()
				throw IOException("Read Thread Interrupted")
			}
			x = bais.read()
		}
		return x
	}

	fun add(arr: ByteArray) {
		deque.putLast(arr)
	}

	override fun close() {
		close = true
	}
}

fun File.createInputStream(): InputStream {
	return BufferedInputStream(FileInputStream(this))
}

fun File.createOutputStream(append: Boolean = false): OutputStream {
	return BufferedOutputStream(FileOutputStream(this, append))
}

fun ByteBuffer.toInputStream(): InputStream {
	return object : InputStream() {
		override fun read(): Int {
			return if (hasRemaining()) {
				get().toInt() and 0xFF
			} else {
				-1
			}
		}
	}
}

fun ByteBuffer.toDataInputStream(): DataInputStream {
	return DataInputStream(this.toInputStream())
}

object SafeMapping {
	private val hexArr = "0123456789ABCDEF".toCharArray()
	private val safeExtra = " `!@#$&()-_+=[]{};',.".toHashSet()
	private val unsafeWords = arrayOf(
		"CON",
		"CONIN$",
		"CONOUT$",
		"PRN",
		"AUX",
		"CLOCK$",
		"NUL",
		"COM0",
		"COM1",
		"COM2",
		"COM3",
		"COM4",
		"COM5",
		"COM6",
		"COM7",
		"COM8",
		"COM9",
		"LPT0",
		"LPT1",
		"LPT2",
		"LPT3",
		"LPT4",
		"LPT5",
		"LPT6",
		"LPT7",
		"LPT8",
		"LPT9",
		"KEYBD$",
		"SCREEN$",
		"\$IDLE$",
		"CONFIG$",
		"\$Mft",
		"\$MftMirr",
		"\$LogFile",
		"\$Volume",
		"\$AttrDef",
		"\$Bitmap",
		"\$Boot",
		"\$BadClus",
		"\$Secure",
		"\$Upcase",
		"\$Extend",
		"\$Quota",
		"\$ObjId",
		"\$Reparse"
	).toHashSet()

	fun isSafeCharacter(ch: Char): Boolean {
		return ch.isLetterOrDigit() || (ch in safeExtra)
	}

	fun toSafe(str: String): String {
		val sb = StringBuilder()
		if (str in unsafeWords) {
			return "US$str"
		}

		for (c in str) {
			if (isSafeCharacter(c)) {
				sb.append(c)
			} else {
				sb.append('~')
				val code = c.code
				repeat(4) {
					sb.append(hexArr[(code ushr (12 - (4 * it))) and 0xF])
				}
			}
		}
		return sb.toString()
	}

	fun fromSafe(str: String): String {
		val sb = StringBuilder()
		var decodeMode = false
		val num = StringBuilder()
		for (c in str) {
			if (c == '~') {
				decodeMode = true
			} else if (decodeMode) {
				if ((c !in '0'..'9') && (c !in 'A'..'F')) {
					decodeMode = false
					sb.append(num.toString())
					num.clear()
					continue
				} else {
					num.append(c)
				}
			} else {
				sb.append(c)
			}
			if (num.length == 4) {
				decodeMode = false
				sb.append(num.toString().toInt(16).toChar())
				num.clear()
			}
		}
		if (num.isNotEmpty()) {
			sb.append(num)
		}
		return sb.toString()
	}
}

fun String.toFileSafe(): String {
	return SafeMapping.toSafe(this)
}

fun String.fromFileSafe(): String {
	return SafeMapping.fromSafe(this)
}

fun getExecutableDirectoryOrJar(): File {
	val path = (object {}).javaClass.protectionDomain?.codeSource?.location?.toURI() ?: File(".").toURI()
	return File(path)
}

class DirectoryIterable(val file: File) : Iterable<Content> {
	override fun iterator(): Iterator<Content> {
		return object : Iterator<Content> {
			val fileIterator = file.walkTopDown().filter { it.isFile }.iterator()
			override fun next(): Content {
				val f = fileIterator.next()
				return FileContent(f, file.consistentPath())
			}

			override fun hasNext(): Boolean {
				return fileIterator.hasNext()
			}
		}
	}
}

class ZipIterable(file: File) : Iterable<Content> {
	val zipFile = ZipFile(file)
	override fun iterator(): Iterator<Content> {
		return object : Iterator<Content> {
			val iter = zipFile.entries()
			override fun next(): Content {
				return ZipEntryContent(zipFile, iter.nextElement())
			}

			override fun hasNext(): Boolean {
				return iter.hasMoreElements()
			}
		}
	}
}

fun File.iterable(): Iterable<Content> {
	return if (this.isDirectory) {
		DirectoryIterable(this)
	} else if (this.name.endsWith(".zip")) {
		ZipIterable(this)
	} else {
		error("Don't know how to iterate through file $this")
	}
}

fun closeAll(closeables: List<Closeable>) {
	for (c in closeables) {
		try {
			c.close()
		} catch (e: Exception) {
			log(e.toString())
		}
	}
}

fun closeAll(vararg closeables: Closeable) {
	for (c in closeables) {
		try {
			c.close()
		} catch (e: Exception) {
			log(e.toString())
		}
	}
}

fun closeAll(caller: Any, closeables: Collection<Closeable>) {
	for (c in closeables) {
		try {
			c.close()
		} catch (e: Exception) {
			log("$caller::class: $e")
		}
	}
}

val currentDir: File
	get() {
		val dir = File((System.getProperty("user.dir") ?: "."))
		return if (dir.exists()) {
			dir
		} else {
			File(".").absoluteFile
		}
	}

fun File.consistentPath(): String {
	return this.absoluteFile.normalize().invariantSeparatorsPath + if (this.isDirectory) "/" else ""
}

fun File.consistentFile(): File {
	return File(this.consistentPath())
}

fun makeRelativePath(baseDir: File, childFile: File): String {
	val base = baseDir.consistentPath()
	val child = childFile.consistentPath()
	return if (child.startsWith(base)) {
		child.substring(base.length)
	} else {
		childFile.name
	}
}

fun File.contains(child: File): Boolean {
	val cf = child.consistentPath()
	val pf = this.consistentPath()
	return cf.startsWith(pf)
}

fun Throwable.isNormalSocketClose(socket: Socket): Boolean {
	// quick checks for common, harmless close conditions
	when (this) {
		is EOFException,
		is InterruptedIOException,
		is java.nio.channels.ClosedByInterruptException,
		is java.nio.channels.ClosedChannelException -> return true

		is java.net.SocketException -> {
			val msg = message?.lowercase() ?: ""
			if (msg.contains("socket closed") || msg.contains("broken pipe") || msg.contains("connection reset")) return true
		}

		is IOException -> {
			val msg = message?.lowercase() ?: ""
			if (msg.contains("stream closed") || msg.contains("connection reset") || msg.contains("connection aborted")) return true
		}
	}
	return socket.isClosed
}