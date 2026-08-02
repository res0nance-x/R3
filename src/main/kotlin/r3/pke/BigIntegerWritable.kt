package r3.pke

import r3.io.Writable
import r3.source.BlockWritable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger

data class BigIntegerWritable(val bi: BigInteger) : Writable {
	override fun write(dos: DataOutputStream) {
		BlockWritable(bi.toByteArray()).write(dos)
	}

	companion object {
		fun read(dis: DataInputStream): BigIntegerWritable {
			return BigIntegerWritable(BigInteger(BlockWritable.read(dis).arr))
		}
	}
}