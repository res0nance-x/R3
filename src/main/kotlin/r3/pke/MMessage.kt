package r3.pke

import r3.hash.hash256
import r3.io.Writable
import r3.io.serialize
import r3.key.Key256
import r3.math.Matrix
import r3.math.prime512
import r3.source.BlockWritable
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class MMessage(
	val version: Int,
	val to: Key256,
	val from: Key256,
	val my: Matrix,
	val encryptedMeta: BlockWritable,
	val attachKey: Key256,
	val attachLength: Long,
	val signature: Signature
) : Writable {
	val key = Key256(signature.serialize().hash256())
	override fun write(dos: DataOutputStream) {
		dos.writeInt(version)
		to.write(dos)
		from.write(dos)
		my.write(dos)
		encryptedMeta.write(dos)
		attachKey.write(dos)
		dos.writeLong(attachLength)
		signature.write(dos)
	}

	fun verify(peer: MPeer): Boolean {
		val baos = ByteArrayOutputStream()
		val os = DataOutputStream(baos)
		os.writeInt(version)
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
			version: Int,
			to: MPeer,
			from: MIdentity,
			my: Matrix,
			encryptedMeta: BlockWritable,
			attachKey: Key256,
			attachLength: Long
		): Signature {
			val baos = ByteArrayOutputStream()
			val os = DataOutputStream(baos)
			os.writeInt(version)
			to.key.write(os)
			from.key.write(os)
			my.write(os)
			encryptedMeta.write(os)
			attachKey.write(os)
			os.writeLong(attachLength)
			os.close()
			return from.sign(baos.toByteArray())
		}

		fun read(dis: DataInputStream): MMessage {
			val version = dis.readInt()
			val to = Key256.read(dis)
			val from = Key256.read(dis)
			val my = Matrix.read(dis, prime512)
			val encryptedMeta = BlockWritable.read(dis)
			val attach = Key256.read(dis)
			val attachLength = dis.readLong()
			val signature = Signature.read(dis)
			return MMessage(version, to, from, my, encryptedMeta, attach, attachLength, signature)
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
		other as MMessage
		return key == other.key
	}

	override fun hashCode(): Int {
		return key.hashCode()
	}
}