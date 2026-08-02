package r3.net.udp
/*
import r3.encryption.createCipherKey
import r3.io.Writable
import r3.io.serialize
import r3.io.toDataInputStream
import r3.key.Key256
import r3.net.createDatagramSocket
import r3.net.hasIP6
import r3.net.topicToIP4Address
import r3.net.topicToIP6Address
import r3.pke.Identity
import r3.source.BlockWritable
import r3.source.StringWritable
import r3.util..Cache
import r3.util.srnd
import r3.util.warningMessage
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import kotlin.concurrent.thread

class MulticastNode(
	private val identity: Identity,
	inf: NetworkInterface,
	topic: String,
	val handler: (peerID: Key256, name: String, data: ByteArray) -> Unit
) {
	class MessageType<T : Writable>(val name: String, val read: (DataInputStream) -> T)

	private val cipherKey = createCipherKey(topic)
	private val r3.g.encrypt = cipherKey.createEncrypt()
	private val decrypt = cipherKey.createDecrypt()
	private val ipMulticastAddress: InetSocketAddress = run {
		if (inf.hasIP6()) {
			topicToIP6Address(topic)
		} else {
			topicToIP4Address(topic)
		}
	}
	private val mcSocket = MulticastSocket(ipMulticastAddress.port)
	private val socket = inf.createDatagramSocket()

	init {
		mcSocket.joinGroup(ipMulticastAddress, inf)
		thread(isDaemon = true, block = ::listenLoop)
	}

	private fun listenLoop() {
		val cache = .Cache<Long, Long>(1000)
		while (true) {
			try {
				val d = DatagramPacket(ByteArray(1280), 0, 1280)
				mcSocket.receive(d)
				println("Received " + d.address)
				val dec = decrypt.doFinal(d.data)
				dec.toDataInputStream().use {
					val msgID = it.readLong()
					if (!cache.containsKey(msgID)) {
						cache[msgID] = msgID
						val peerID = Key256.read(it)
						val name = StringWritable.read(it)
						val block = BlockWritable.read(it)
						handler(peerID, name.str, block.array)
					}
				}
			} catch (e: Exception) {
				// might be a packet not intended for us, or could be our error
				e.printStackTrace()
				warningMessage(e)
			}
		}
	}

	fun <T : Writable> send(type: MessageType<T>, msg: T) {
		send(type.name, msg.serialize())
	}

	// array must be less than packet size minus overhead. To be safe we use 1100
	// this leaves around 180 bytes free for name, headers and metadata
	private fun send(name: String, arr: ByteArray) {
		// 50 characters is a very long name indeed
		if (name.length > 50) {
			throw RuntimeException("name too long")
		}
		if (arr.size > 1100) {
			throw RuntimeException("array exceeds maximum supported array size")
		}
		val baos = ByteArrayOutputStream()
		val dos = DataOutputStream(baos)
		dos.writeLong(srnd.nextLong()) // 8 bytes
		identity.key.write(dos) // 32 bytes
		StringWritable(name).write(dos) // 4 + name.length
		BlockWritable(arr).write(dos) // arr.size + 4
		// pad to be multiple of 16 bytes (128 bits)
		while (baos.size() % 16 != 0) {
			baos.write(srnd.nextInt() and 0xFF)
		}
		val data = baos.toByteArray()
		val enc = r3.g.encrypt.doFinal(data)
		val packet = DatagramPacket(enc, 0, enc.size, ipMulticastAddress)
		println("Sending packet " + packet.address)
		// Send twice in case 1 packet is lost. If both are lost, probably congestion and sending more packets
		// unlikely to help. For our case the odd message not getting through is acceptable.
		socket.send(packet)
		socket.send(packet)
	}

	companion object {
		val HELLO = MessageType("hello", StringWritable::read)
		val PEER_REQUEST = MessageType("peer_request", PeerRequest::read)
		val PEER_REPLY = MessageType("peer_reply", PeerReply::read)
		val PEER_ADDRESS_REQUEST = MessageType("peer_address_request", PeerAddressRequest::read)
		val PEER_ADDRESS_REPLY = MessageType("peer_address_reply", PeerAddressReply::read)
		val SHORT_TEXT_MESSAGE = MessageType("short_text_message", StringWritable::read)
		val CONTENT_REFERENCE = MessageType("content_reference", ContentReference::read)
	}
}
 */