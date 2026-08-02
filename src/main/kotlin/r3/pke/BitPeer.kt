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
import r3.key.Key256
import r3.math.BitMatrix
import r3.util.srnd
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest

class BitSharedKey(val my: BitMatrix, val cipherKey: CipherKey)
class BitPeer(val mx: BitMatrix) : Writable {
	val m = BitMatrix.read(bit127.fromUBase64().toDataInputStream())
	val key: Key256
		get() = Key256(mx.serialize().hash256())

	fun verify(arr: ByteArray, sign: Signature): Boolean {
		return verify(ByteArrayInputStream(arr), sign)
	}

	fun verify(stream: InputStream, sign: Signature): Boolean {
		val md = MessageDigest.getInstance("SHA-256")
		val tr = m.mpow(sign.s) * (mx.mpow(sign.h))
		md.update(tr.serialize())
		val th = BigInteger(1, hash(md, stream))
		return sign.h == th
	}

	fun createShared(): BitSharedKey {
		val y = BigInteger(1, srnd.getByteArray(primeBits))
		val mxy = mx.mpow(y)
		return BitSharedKey(m.mpow(y), createCipherKey(mxy))
	}

	override fun write(dos: DataOutputStream) {
		m.serialize().also { barr ->
			dos.writeShort(barr.size)
			dos.write(barr)
		}
		mx.serialize().also { barr ->
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
	}

	override fun toString(): String {
		return "Peer: " + key.serialize().toUBase64()
	}
}
