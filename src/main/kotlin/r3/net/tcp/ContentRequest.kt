package r3.net.tcp

import r3.pke.ContentKey
import r3.pke.PeerKey
import java.io.DataInputStream

class ContentRequest(val contentKey: ContentKey, val peerKey: PeerKey) {
	companion object {
		fun read(dis: DataInputStream): ContentRequest {
			return ContentRequest(
				ContentKey.read(dis),
				PeerKey.read(dis)
			)
		}
	}
}