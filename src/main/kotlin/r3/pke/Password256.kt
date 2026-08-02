package r3.pke

import r3.hash.fromUBase64
import r3.key.Key256
import r3.util.srnd
import java.io.DataInputStream

class Password256(arr: ByteArray) : Key256(arr) {
	constructor(str: String) : this(str.fromUBase64())
	constructor(key: Key256) : this(key.arr)

	companion object {
		fun read(dis: DataInputStream): Password256 {
			return Password256(Key256.read(dis).arr)
		}

		fun createPassword(): Password256 {
			return Password256(srnd.getByteArray(32))
		}
	}
}