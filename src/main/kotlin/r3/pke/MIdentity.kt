package r3.pke

import r3.encryption.CipherKey
import r3.hash.hash
import r3.hash.hash256
import r3.hash.toUBase64
import r3.io.Writable
import r3.io.readBigInteger
import r3.io.serialize
import r3.io.write
import r3.key.Key256
import r3.math.Matrix
import r3.math.prime512
import r3.util.srnd
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.math.BigInteger.TWO
import java.security.MessageDigest

class MIdentity : Writable {
	val m: Matrix
	val x: BigInteger
	val key: Key256
		get() = Key256(m.mpow(x).serialize().hash256())

	constructor() {
		val a = prime512.pow(2) + prime512 + BigInteger.ONE
		val b = (prime512 - BigInteger.ONE).divide(TWO)
		val factor = arrayOf(TWO, a, b)
		m = Matrix.getMaximal(3, prime512, factor)
		x = BigInteger(1, srnd.getByteArray(prime512.bitLength() * 3)).mod(m.inversionPower())
	}

	constructor(m: Matrix, x: BigInteger) {
		this.m = m
		this.x = x
	}

	fun sign(istream: InputStream): Signature {
		val md = MessageDigest.getInstance("SHA-256")
		val ip = m.inversionPower()
		val k = BigInteger(1, srnd.getByteArray(m.inversionPower().bitLength()))
		val r = m.mpow(k)
		md.update(r.serialize())
		val h = BigInteger(1, hash(md, istream))
		val mxh = ip - (x * h).mod(ip)
		val s = (k + mxh).mod(ip)
		return Signature(h, s)
	}

	fun sign(arr: ByteArray): Signature {
		return sign(ByteArrayInputStream(arr))
	}

	fun asPeer(): MPeer {
		return MPeer(m, m.mpow(x))
	}

	fun createCipherKey(my: Matrix): CipherKey {
		val mxy = my.mpow(x)
		return r3.encryption.createCipherKey(mxy)
	}

	override fun write(dos: DataOutputStream) {
		m.write(dos)
		x.write(dos)
	}

	companion object {
		fun read(dis: DataInputStream): MIdentity {
			val m = Matrix.read(dis, prime512)
			val x = readBigInteger(dis)
			return MIdentity(m, x)
		}
	}

	override fun toString(): String {
		return "MIdentity: " + key.serialize().toUBase64()
	}
}