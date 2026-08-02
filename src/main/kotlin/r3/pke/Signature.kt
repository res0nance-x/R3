package r3.pke

import r3.io.Writable
import r3.io.readBigInteger
import r3.io.write
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger

data class Signature(val h: BigInteger, val s: BigInteger) : Writable {
	override fun write(dos: DataOutputStream) {
		h.write(dos)
		s.write(dos)
	}

	companion object {
		fun read(dis: DataInputStream): Signature {
			return Signature(readBigInteger(dis), readBigInteger(dis))
		}
	}

	override fun toString(): String {
		return "${h.toString(32)} ${s.toString(32)}"
	}
}