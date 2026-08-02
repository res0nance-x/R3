package r3.repository

import r3.io.Writable
import r3.pke.Identity
import r3.source.StringWritable
import java.io.DataInputStream
import java.io.DataOutputStream

class IdentityAliasPair(val identity: Identity, val alias: String) : Writable {
	override fun write(dos: DataOutputStream) {
		identity.write(dos)
		StringWritable(alias).write(dos)
	}

	companion object {
		fun read(dis: DataInputStream): IdentityAliasPair {
			return IdentityAliasPair(Identity.read(dis), StringWritable.read(dis).str)
		}
	}
}