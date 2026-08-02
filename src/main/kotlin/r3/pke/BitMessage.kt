package r3.pke

import r3.hash.hash256
import r3.io.Writable
import r3.io.serialize
import r3.key.Key256
import r3.math.BitMatrix
import r3.source.BlockWritable
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class BitMessage(
	val to: Key256,
	val from: Key256,
	val my: BitMatrix,
	val encryptedMeta: BlockWritable,
	val attachKey: Key256,
	val attachLength: Long,
	val signature: Signature
) : Writable {
	val key = Key256(signature.serialize().hash256())
	override fun write(dos: DataOutputStream) {
		to.write(dos)
		from.write(dos)
		my.write(dos)
		encryptedMeta.write(dos)
		attachKey.write(dos)
		dos.writeLong(attachLength)
		signature.write(dos)
	}

	//	fun getMeta(iden: BitIdentity): RelayMessageMeta {
//		return RelayMessageMeta.read(
//			DataInputStream(
//				ByteArrayInputStream(
//					iden.createCipherKey(my).createDecrypt().doFinal(encryptedMeta.serialize())
//				)
//			)
//		)
//	}
	fun verify(peer: MPeer): Boolean {
		val baos = ByteArrayOutputStream()
		val os = DataOutputStream(baos)
		to.write(os)
		from.write(os)
		my.write(os)
		encryptedMeta.write(os)
		attachKey.write(os)
		os.writeLong(attachLength)
		return peer.verify(baos.toByteArray(), signature)
	}

	companion object {
		fun sign(
			to: BitPeer,
			from: BitIdentity,
			my: BitMatrix,
			encryptedMeta: BlockWritable,
			attachKey: Key256,
			attachLength: Long
		): Signature {
			val baos = ByteArrayOutputStream()
			val os = DataOutputStream(baos)
			to.key.write(os)
			from.key.write(os)
			my.write(os)
			encryptedMeta.write(os)
			attachKey.write(os)
			os.writeLong(attachLength)
			os.close()
			return from.sign(baos.toByteArray())
		}

		//		fun createMessage(
//			to: BitPeer,
//			from: BitIdentity,
//			attachKey: Key256,
//			attachLength: Long,
//			meta: RelayMessageMeta
//		): BitMessage {
//			val shared = to.createShared()
//			val r3.g.encrypt = shared.cipherKey.createEncrypt()
//			val encryptedMeta = BlockWritable(r3.g.encrypt.doFinal(padded(meta.serialize())))
//			return BitMessage(
//				to.key,
//				from.key,
//				shared.my,
//				encryptedMeta,
//				attachKey,
//				attachLength,
//				r3.g.sign(to, from, shared.my, encryptedMeta, attachKey, attachLength)
//			)
//		}
		fun read(dis: DataInputStream): BitMessage {
			val to = Key256.read(dis)
			val from = Key256.read(dis)
			val my = BitMatrix.read(dis)
			val encryptedMeta = BlockWritable.read(dis)
			val attach = Key256.read(dis)
			val attachLength = dis.readLong()
			val signature = Signature.read(dis)
			return BitMessage(to, from, my, encryptedMeta, attach, attachLength, signature)
		}
	}

	override fun toString(): String {
		return "Message: $key"
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (javaClass != other?.javaClass) {
			return false
		}
		other as BitMessage
		return key == other.key
	}

	override fun hashCode(): Int {
		return key.hashCode()
	}
}