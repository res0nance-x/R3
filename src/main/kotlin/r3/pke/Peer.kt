package r3.pke

import r3.encryption.CipherKey
import r3.encryption.createCipherKey
import r3.hash.fromUBase64
import r3.hash.hash
import r3.hash.hash256
import r3.hash.toUBase64
import r3.io.Writable
import r3.io.serialize
import r3.io.toDataInputStream
import r3.util.srnd
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest

class SharedKey(val my: BigInteger, val cipherKey: CipherKey) {
	operator fun component1(): BigInteger {
		return my
	}

	operator fun component2(): CipherKey {
		return cipherKey
	}
}

class Peer(val m: BigInteger, val mx: BigInteger) : Writable {
	val key: PeerKey
		get() = PeerKey(mx.toByteArray().hash256())

	fun verify(arr: ByteArray, sign: Signature): Boolean {
		return verify(ByteArrayInputStream(arr), sign)
	}

	fun verify(stream: InputStream, sign: Signature): Boolean {
		val md = MessageDigest.getInstance("SHA-256")
		val tr = (m.modPow(sign.s, prime) * (mx.modPow(sign.h, prime))).mod(prime)
		md.update(tr.toByteArray())
		val th = BigInteger(1, hash(md, stream))
		return sign.h == th
	}

	fun createShared(): SharedKey {
		val y = BigInteger(1, srnd.getByteArray(primeBits))
		val mxy = mx.modPow(y, prime)
		return SharedKey(m.modPow(y, prime), createCipherKey(mxy))
	}

	override fun write(dos: DataOutputStream) {
		m.toByteArray().also { barr ->
			dos.writeShort(barr.size)
			dos.write(barr)
		}
		mx.toByteArray().also { barr ->
			dos.writeShort(barr.size)
			dos.write(barr)
		}
	}

	companion object {
		fun read(dis: DataInputStream): Peer {
			val m = run {
				val len = dis.readUnsignedShort()
				val ba = ByteArray(len)
				dis.read(ba)
				BigInteger(1, ba)
			}
			val mx = run {
				val len = dis.readUnsignedShort()
				val ba = ByteArray(len)
				dis.read(ba)
				BigInteger(1, ba)
			}
			return Peer(m, mx)
		}

		fun import(map: Map<String, String>): Peer {
			val data = map["data"]?.fromUBase64() ?: error("No data field found")
			return read(data.toDataInputStream())
		}
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (javaClass != other?.javaClass) {
			return false
		}

		other as Peer

		return key == other.key
	}

	override fun hashCode(): Int {
		return key.hashCode()
	}

	override fun toString(): String {
		return "Peer: " + key.serialize().toUBase64()
	}
}