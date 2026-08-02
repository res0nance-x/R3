package r3.encryption

import r3.math.MatrixSequence
import r3.util.srnd
import java.io.OutputStream

// Encoded in chunks to allow us to InputStream.skip when reading
class EncryptedBlockOutputStream(private val cipher: CipherKey, private val os: OutputStream) :
	OutputStream() {
	private val blockSize = 4096
	private val buf = ByteArray(blockSize)
	private var pos = 0
	private var closed = false
	private val sequence =
		MatrixSequence(cipher.iv) // Used to ensure identical blocks in different regions are not encoded the same
	val encrypt = cipher.createEncrypt()
	private var blockNum = 0L
	override fun write(b: Int) {
		buf[pos++] = b.toByte()
		if (pos == blockSize) {
			val padBlock = sequence.getSequence(blockNum)
			encrypt.update(padBlock)
			++blockNum
			os.write(encrypt.doFinal(buf))
			pos = 0
		}
	}

	override fun flush() {
		if (pos > 0) {
			for (x in pos until blockSize) {
				buf[x] = srnd.nextInt().toByte()
			}
			os.write(encrypt.doFinal(buf))
		}
		os.flush()
	}

	override fun close() {
		if (!closed) {
			flush()
			os.close()
			closed = true
		}
	}
}