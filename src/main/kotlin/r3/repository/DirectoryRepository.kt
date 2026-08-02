package r3.repository

import r3.collection.MutableSimpleMap
import r3.encryption.CipherKey
import r3.key.Key256
import r3.pke.Identity
import r3.pke.Peer
import java.io.File

class DirectoryRepository(cipherKey: CipherKey, dir: File) {
	val identities: MutableSimpleMap<Key256, IdentityAliasPair> = r3.collection.EncryptedDirMap(
		cipherKey,
		File(dir, "identity").apply { if (!exists()) mkdirs() },
		{ dos, key -> key.write(dos) },
		Key256::read,
		{ dos, pair -> pair.write(dos) },
		IdentityAliasPair.Companion::read
	)
	val peers: MutableSimpleMap<Key256, PeerAliasPair> = r3.collection.EncryptedDirMap(
		cipherKey,
		File(dir, "peer").apply { if (!exists()) mkdirs() },
		{ dos, key -> key.write(dos) },
		Key256::read,
		{ dos, pair -> pair.write(dos) },
		PeerAliasPair.Companion::read
	)
//	val messages: MutableSimpleMap<Key256, Message> = r3.collection.EncryptedDirMap(
//			cipherKey,
//			File(dir, "message").apply { if (!exists()) mkdirs() },
//			{ dos, key -> key.write(dos) },
//			Key256::read,
//			{ dos, msg -> msg.write(dos) },
//			Message::read
//	)
}

fun MutableSimpleMap<Key256, IdentityAliasPair>.find(alias: String): Identity? {
	for (k in keys) {
		get(k)?.also { v ->
			if (v.alias == alias) {
				return v.identity
			}
		}
	}
	return null
}

fun MutableSimpleMap<Key256, PeerAliasPair>.find(alias: String): Peer? {
	for (k in keys) {
		get(k)?.also { v ->
			if (v.alias == alias) {
				return v.peer
			}
		}
	}
	return null
}