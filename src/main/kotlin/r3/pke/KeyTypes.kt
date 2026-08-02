package r3.pke

import r3.app.NameGen
import r3.hash.fromUBase64
import r3.key.Key256
import java.io.DataInputStream

class EncryptedMetaKey(arr: ByteArray) : Key256(arr) {
	constructor(str: String) : this(str.fromUBase64())
	constructor(key: Key256) : this(key.arr)

	companion object {
		fun read(dis: DataInputStream): EncryptedMetaKey {
			return EncryptedMetaKey(Key256.read(dis).arr)
		}
	}
}

class MetaKey(arr: ByteArray) : Key256(arr) {
	constructor(str: String) : this(str.fromUBase64())
	constructor(key: Key256) : this(key.arr)

	companion object {
		fun read(dis: DataInputStream): MetaKey {
			return MetaKey(Key256.read(dis).arr)
		}
	}
}

class ServerKey(arr: ByteArray) : Key256(arr) {
	constructor(str: String) : this(str.fromUBase64())
	constructor(key: Key256) : this(key.arr)

	companion object {
		fun read(dis: DataInputStream): ServerKey {
			return ServerKey(Key256.read(dis).arr)
		}
	}
}

class ContentKey(arr: ByteArray) : Key256(arr) {
	constructor(str: String) : this(str.fromUBase64())
	constructor(key: Key256) : this(key.arr)

	companion object {
		fun read(dis: DataInputStream): ContentKey {
			return ContentKey(Key256.read(dis).arr)
		}
	}
}

class IdentityKey(arr: ByteArray) : Key256(arr) {
	constructor(str: String) : this(str.fromUBase64())
	constructor(key: Key256) : this(key.arr)

	companion object {
		fun read(dis: DataInputStream): IdentityKey {
			return IdentityKey(Key256.read(dis).arr)
		}
	}
}

class PeerKey(arr: ByteArray) : Key256(arr) {
	constructor(str: String) : this(str.fromUBase64())
	constructor(key: Key256) : this(key.arr)

	companion object {
		fun read(dis: DataInputStream): PeerKey {
			return PeerKey(Key256.read(dis).arr)
		}
	}
}

private val nameGen = NameGen()
val Key256.name: String
	get() {
		return nameGen.generateName(this)
	}
val IdentityKey.name: String
	get() {
		return nameGen.generateName(Key256(this.arr))
	}
val PeerKey.name: String
	get() {
		return nameGen.generateName(Key256(this.arr))
	}
val ServerKey.name: String
	get() {
		return nameGen.generateName(Key256(this.arr))
	}