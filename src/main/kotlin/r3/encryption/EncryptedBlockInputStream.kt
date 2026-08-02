package r3.encryption

import r3.io.readFully
import r3.io.skipFullBytes
import r3.math.MatrixSequence
import java.io.InputStream

class EncryptedBlockInputStream(
	cipher: CipherKey,
	private val iis: InputStream
) : InputStream() {
	private val blockSize = 4096
	private val raw = ByteArray(blockSize)
	private var buf = ByteArray(blockSize)
	private var fpos = 0L
	private var rpos = 0L
	private val encrypt = cipher.createEncrypt()
	private val decrypt = cipher.createDecrypt()
	private val sequence = MatrixSequence(cipher.iv)
	private fun readBlock(): Boolean {
		val blockNum = fpos / blockSize
		val padBlock = sequence.getSequence(blockNum)
		encrypt.doFinal(padBlock, 0, 16, padBlock)
		decrypt.update(padBlock, 0, 16, padBlock)
		val last = iis.readFully(raw) < blockSize
		decrypt.doFinal(raw, 0, blockSize, buf)
		fpos += blockSize
		return last
	}

	override fun read(): Int {
		if (rpos >= fpos) {
			val skip = (rpos - fpos) / blockSize * blockSize
			if (skip > 0) {
				iis.skipFullBytes(skip)
				fpos += skip
			}
			if (readBlock()) {
				return -1
			}
		}
		val b = buf[blockSize - (fpos - rpos).toInt()].toInt() and 0xFF
		++rpos
		return b
	}

	override fun skip(n: Long): Long {
		if (n < 0) {
			throw Exception("Can not skip backwards")
		}
		rpos += n
		return n
	}
}