package r3.pke

import r3.encryption.CipherKey
import r3.encryption.createCipherKey
import r3.hash.hash
import r3.hash.hash256
import r3.hash.toUBase64
import r3.io.Writable
import r3.io.serialize
import r3.key.Key256
import r3.math.Matrix
import r3.math.prime512
import r3.util.srnd
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest

class MSharedKey(val my: Matrix, val cipherKey: CipherKey)
class MPeer(val m: Matrix, val mx: Matrix) : Writable {
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

	fun createShared(): MSharedKey {
		val y = BigInteger(1, srnd.getByteArray(m.inversionPower().bitLength())).mod(m.inversionPower())
		val mxy = mx.mpow(y)
		return MSharedKey(m.mpow(y), createCipherKey(mxy))
	}

	override fun write(dos: DataOutputStream) {
		m.write(dos)
		mx.write(dos)
	}

	companion object {
		fun read(dis: DataInputStream): MPeer {
			val m = Matrix.read(dis, prime512)
			val mx = Matrix.read(dis, prime512)
			return MPeer(m, mx)
		}
	}

	override fun toString(): String {
		return "Peer: " + key.serialize().toUBase64()
	}
}