package r3.repository

import r3.io.Writable
import r3.pke.Peer
import r3.source.StringWritable
import java.io.DataInputStream
import java.io.DataOutputStream

class PeerAliasPair(val peer: Peer, val alias: String) : Writable {
	override fun write(dos: DataOutputStream) {
		peer.write(dos)
		StringWritable(alias).write(dos)
	}

	companion object {
		fun read(dis: DataInputStream): PeerAliasPair {
			return PeerAliasPair(Peer.read(dis), StringWritable.read(dis).str)
		}
	}
}