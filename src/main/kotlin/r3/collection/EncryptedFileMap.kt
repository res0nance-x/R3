package r3.collection

import r3.encryption.CipherKey
import r3.encryption.padded
import r3.io.GenericWritable
import r3.io.serialize
import r3.io.toDataInputStream
import r3.source.BlockWritable
import java.io.DataInputStream
import java.io.DataOutputStream

// Caches all decrypted keys/values as well
class EncryptedFileMap<K, V>(
	cipherKey: CipherKey,
	private val sMap: MutableSimpleMap<BlockWritable, BlockWritable>,
	private val writeKey: (DataOutputStream, key: K) -> Unit,
	private val readKey: (DataInputStream) -> K,
	private val writeValue: (DataOutputStream, value: V) -> Unit,
	private val readValue: (DataInputStream) -> V
) : MutableSimpleMap<K, V> {
	private val encrypt = cipherKey.createEncrypt()
	private val decrypt = cipherKey.createDecrypt()
	private val map = LinkedHashMap<K, V>()

	init {
		sMap.visit { k, v ->
			val key = decrypt.doFinal(k.arr).toDataInputStream().use(readKey)
			map[key] = decrypt.doFinal(v.arr).toDataInputStream().use(readValue)
		}
	}

	override val size: Int
		get() = keys.size
	override val keys: Set<K>
		get() = HashSet(map.keys)

	override fun get(key: K): V? {
		return map[key]
	}

	override fun set(key: K, value: V) {
		val ek = encrypt.doFinal(padded(GenericWritable(key, writeKey).serialize()))
		val ev = encrypt.doFinal(padded(GenericWritable(value, writeValue).serialize()))
		val action = if (map.contains(key)) UpdateType.CHANGE else UpdateType.ADD
		map[key] = value
		sMap[BlockWritable(ek)] = BlockWritable(ev)
	}

	override fun remove(key: K) {
		map.remove(key)
		val ek = BlockWritable(encrypt.doFinal(padded(GenericWritable(key, writeKey).serialize())))
		sMap.remove(ek)
	}

	override fun visit(visitor: (K, V) -> Unit) {
		for (x in map) {
			visitor(x.key, x.value)
		}
	}
}