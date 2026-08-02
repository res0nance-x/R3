package r3.collection

import r3.encryption.CipherKey
import r3.io.toDataInputStream
import r3.source.BlockWritable
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

class EncryptedDirMap<K, V>(
	private val cipherKey: CipherKey,
	dir: File,
	private val writeKey: (DataOutputStream, key: K) -> Unit,
	private val readKey: (DataInputStream) -> K,
	private val writeValue: (DataOutputStream, value: V) -> Unit,
	private val readValue: (DataInputStream) -> V
) : MutableSimpleMap<K, V> {
	private val dirMap = DirMap(
		dir,
		{ dos, key -> key.write(dos) },
		{ dis -> BlockWritable.read(dis) },
		{ dos, value -> value.write(dos) },
		{ dis -> BlockWritable.read(dis) }
	)

	private fun keytoEncryptedBlock(key: K): BlockWritable {
		val baos = ByteArrayOutputStream()
		DataOutputStream(baos).use { dos ->
			writeKey(dos, key)
		}
		val arr = baos.toByteArray()
		return encryptBlockWritable(cipherKey, BlockWritable(arr))
	}

	private fun encryptedBlockToKey(block: BlockWritable): K {
		val dblock = decryptBlockWritable(cipherKey, block)
		return readKey(dblock.arr.toDataInputStream())
	}

	private fun valuetoEncryptedBlock(value: V): BlockWritable {
		val baos = ByteArrayOutputStream()
		DataOutputStream(baos).use { dos ->
			writeValue(dos, value)
		}
		val arr = baos.toByteArray()
		return encryptBlockWritable(cipherKey, BlockWritable(arr))
	}

	private fun encryptedBlockToValue(block: BlockWritable): V {
		val dblock = decryptBlockWritable(cipherKey, block)
		return readValue(dblock.arr.toDataInputStream())
	}

	override fun set(key: K, value: V) {
		dirMap[keytoEncryptedBlock(key)] = valuetoEncryptedBlock(value)
	}

	override fun remove(key: K) {
		dirMap.remove(keytoEncryptedBlock(key))
	}

	override val size: Int
		get() = keys.size
	override val keys: Set<K>
		get() {
			return HashSet(dirMap.keys.map { encryptedBlockToKey(it) })
		}

	override fun visit(visitor: (K, V) -> Unit) {
		dirMap.visit { k, v ->
			visitor(encryptedBlockToKey(k), encryptedBlockToValue(v))
		}
	}

	override fun get(key: K): V? {
		return dirMap[keytoEncryptedBlock(key)]?.let {
			encryptedBlockToValue(it)
		}
	}
}