package r3.net

import r3.io.Writable
import r3.key.Key256
import r3.pke.Peer
import r3.source.StringWritable
import java.io.DataInputStream
import java.io.DataOutputStream

data class Attachment(
	val peer: Peer,
	val contentID: Key256,
	val name: String,
	val contentType: String,
	val length: Long
) : Writable {
	override fun write(dos: DataOutputStream) {
		peer.write(dos)
		contentID.write(dos)
		StringWritable(name).write(dos)
		StringWritable(contentType).write(dos)
		dos.writeLong(length)
	}

	companion object {
		fun read(dis: DataInputStream): Attachment {
			return Attachment(
				Peer.read(dis),
				Key256.read(dis),
				StringWritable.read(dis).str,
				StringWritable.read(dis).str,
				dis.readLong()
			)
		}
	}

	override fun toString(): String {
		return "\tName: $name\n\tContentID: $contentID\n\tType: $contentType\n\tLength:$length"
	}
}

class PublicMessage(
	val from: Key256,
	val msg: String,
	val attachment: Attachment? = null
) : Writable {
	override fun write(dos: DataOutputStream) {
		from.write(dos)
		StringWritable(msg).write(dos)
		if (attachment != null) {
			dos.writeBoolean(true)
			attachment.write(dos)
		} else {
			dos.writeBoolean(false)
		}
	}

	companion object {
		fun read(dis: DataInputStream): PublicMessage {
			return PublicMessage(
				Key256.read(dis),
				StringWritable.read(dis).str,
				if (dis.readBoolean()) {
					Attachment.read(dis)
				} else {
					null
				}
			)
		}
	}

	override fun toString(): String {
		return "From: $from\nMessage: $msg\nr3.net.Attachment:\n $attachment"
	}
}