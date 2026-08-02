package r3.collection

import r3.encryption.CipherKey
import r3.io.serialize
import r3.io.toDataInputStream
import r3.source.BlockWritable

fun encryptBlockWritable(cipherKey: CipherKey, block: BlockWritable): BlockWritable {
	val arr = block.serialize()
	val padding = 16 - arr.size % 16
	val narr = if (padding == 16) {
		arr
	} else {
		val barr = ByteArray(arr.size + padding)
		System.arraycopy(arr, 0, barr, 0, arr.size)
		barr
	}
	val earr = cipherKey.createEncrypt().doFinal(narr)
	return BlockWritable(earr)
}

fun decryptBlockWritable(cipherKey: CipherKey, block: BlockWritable): BlockWritable {
	val arr = block.arr
	val padding = 16 - arr.size % 16
	if (padding != 16) {
		error("Decrypt block size must be multiple of 16")
	}
	val darr = cipherKey.createDecrypt().doFinal(arr)
	return BlockWritable.read(darr.toDataInputStream())
}
