package r3.net

import r3.hash.hash256
import r3.io.log
import r3.io.serialize
import r3.io.toInt
import r3.math.prime128
import r3.util.isAndroid
import java.math.BigInteger
import java.net.*
import java.util.*

fun NetworkInterface.hasIP6(): Boolean {
	for (ip in inetAddresses) {
		if (ip.address.size == 16) {
			if (!ip.isLinkLocalAddress && !ip.isAnyLocalAddress && !ip.isLoopbackAddress) {
				return true
			}
		}
	}
	return false
}

fun NetworkInterface.hasIP4(): Boolean {
	for (ip in inetAddresses) {
		if (ip.address.size == 4) {
			if (!ip.isLinkLocalAddress && !ip.isAnyLocalAddress && !ip.isLoopbackAddress) {
				return true
			}
		}
	}
	return false
}

val loopBackNetworkInterface: NetworkInterface by lazy {
	val list = ArrayList<NetworkInterface>()
	for (ni in NetworkInterface.getNetworkInterfaces()) {
		if (ni.isLoopback) {
			list.add(ni)
		}
	}
	list[0]
}

fun usableNetworkInterfaceList(): List<NetworkInterface> {
	val list = ArrayList<NetworkInterface>()
	for (ni in NetworkInterface.getNetworkInterfaces()) {
		if (ni.supportsMulticast()
			&& !ni.isLoopback
			&& ni.isUp
			&& !ni.isVirtual
			&& ni.inetAddresses.toList()
				.isNotEmpty()
		) {
			list.add(ni)
		}
	}
	return list
}

fun findNetworkInterface(contains: String): NetworkInterface {
	val list = ArrayList<NetworkInterface>()
	for (ni in NetworkInterface.getNetworkInterfaces()) {
		if (ni.isUp && !ni.isVirtual) {
			list.add(ni)
		}
	}
	return list.first { it.name.contains(contains) }
}

fun InetAddress.isIP6SiteLocal(): Boolean {
	return isIP6() && isSiteLocalAddress
}

fun InetAddress.isIP6Anonymous(): Boolean {
	return isIP6() && !isSiteLocalAddress && !isLinkLocalAddress
}

fun InetAddress.isIP6LinkLocal(): Boolean {
	return isIP6() && isLinkLocalAddress
}

fun InetAddress.isIP6(): Boolean {
	return this.address.size == 16
}

fun InetAddress.isIP4SiteLocal(): Boolean {
	return isIP4() && isSiteLocalAddress
}

fun InetAddress.isIP4(): Boolean {
	return this.address.size == 4
}

fun NetworkInterface.getIP4BroadcastAddress(): InetAddress? {
	for (x in interfaceAddresses) {
		if (x.address.isIP4() && x.broadcast != null) {
			return x.broadcast
		}
	}
	return null
}

fun NetworkInterface.getIP6BroadcastAddress(): InetAddress? {
	return InetAddress.getByName("ff02::1")
}

data class InterfaceAddressPair(val inf: NetworkInterface, val addr: InetAddress)

fun NetworkInterface.getIPAddresses(): ArrayList<InterfaceAddressPair> {
	val list = ArrayList<InterfaceAddressPair>()
	for (addr in interfaceAddresses) {
		list.add(InterfaceAddressPair(this, addr.address))
	}
	return list
}

fun topicToIP6Address(topic: String): InetSocketAddress {
	val arr = topic.toByteArray().hash256()
	val bi = BigInteger(1, arr)
	// 49152–65535
	val port = 0xffff - bi.mod(BigInteger.valueOf(16384)).toInt()
	val ipArr = ByteArray(16) {
		when (it) {
			0 -> 0xff.toByte()
			// Link-local scope
			1 -> ((arr[it].toInt() and 0xf0) or 0x02).toByte()
//			1 -> ((arr[it].toInt() and 0xf0) or 0x08).toByte()
			else -> arr[it]
		}
	}
	return InetSocketAddress(Inet6Address.getByAddress(ipArr), port)
}

// 239.0.0.0 to 239.255.255.255
fun topicToIP4Address(topic: String): InetSocketAddress {
	val arr = topic.toByteArray().hash256()
	val bi = BigInteger(1, arr)
	// 49152–65535
	val port = 0xffff - bi.mod(BigInteger.valueOf(16384)).toInt()
	val ipArr = ByteArray(4) {
		when (it) {
			0 -> 239.toByte()
			else -> arr[it]
		}
	}
	return InetSocketAddress(Inet4Address.getByAddress(ipArr), port)
}

fun NetworkInterface.createIP6DatagramSocket(port: Int = 0): DatagramSocket {
	return if (hasIP6()) {
		if (isAndroid()) {
			// Only LinkLocal seems to work
			val addr = getIPAddresses().filter { it.addr.isIP6LinkLocal() }.shuffled()
			if (addr.isEmpty()) {
				throw RuntimeException("Unable to find suitable network address")
			}
			DatagramSocket(InetSocketAddress(addr.first().addr, port)).apply { reuseAddress = true }
		} else {
			var addr = getIPAddresses().filter { it.addr.isIP6Anonymous() }.shuffled()
			if (addr.isEmpty()) {
				addr = getIPAddresses().filter { it.addr.isIP6LinkLocal() }.shuffled()
			}
			if (addr.isEmpty()) {
				addr = getIPAddresses().filter { it.addr.isIP6SiteLocal() }.shuffled()
			}
			if (addr.isEmpty()) {
				throw RuntimeException("Unable to find suitable network address")
			}
			DatagramSocket(InetSocketAddress(addr.first().addr, port)).apply { reuseAddress = true }
		}
	} else throw Exception("IP6 is not available")
}

fun NetworkInterface.createIP4DatagramSocket(port: Int = 0): DatagramSocket {
	return if (hasIP4()) {
		// Only SiteLocal makes sense for IP4
		val addr = getIPAddresses().filter { it.addr.isIP4SiteLocal() && !it.addr.isAnyLocalAddress }
			.shuffled().first()
		val sock = DatagramSocket(InetSocketAddress(addr.addr, port)).apply {
			broadcast = true; reuseAddress = true
		}
		sock
	} else throw Exception("IP4 is not available")
}

val excludedPortSet = setOf(
	1025,
	1080,
	1241,
	1311,
	1433,
	1434,
	1494,
	1512,
	1524,
	1589,
	1701,
	1719,
	1720,
	1723,
	1725,
	1755,
	1812,
	1813,
	1985,
	2000,
	2002,
	2008,
	2010,
	2049,
	2082,
	2083,
	2100,
	2102,
	2103,
	2104,
	2222,
	2401,
	2483,
	2484,
	2809,
	2967,
	3128,
	3222,
	3260,
	3306,
	3389,
	3689,
	3690,
	4321,
	4333,
	4500,
	4899,
	5000,
	5001,
	5004,
	5005,
	5060,
	5061,
	5222,
	5223,
	5353,
	5432,
	5800,
	5900,
	5999,
	6000,
	6001,
	6129,
	6379,
	6588,
	6588,
	8080,
	8200,
	8222,
	8767,
	9042,
	9100,
	9800,
	10161,
	10162,
	13720,
	13721,
	13724,
	13782,
	13783,
	20000,
	22273,
	23399,
	25565,
	27017,
	33434
)

fun getIP6MulticastAddress(): InetSocketAddress {
	val bi = prime128 * BigInteger.valueOf(
		37L
				+ GregorianCalendar().get(GregorianCalendar.YEAR)
				* GregorianCalendar().get(GregorianCalendar.DAY_OF_YEAR)
				* GregorianCalendar().get(GregorianCalendar.HOUR_OF_DAY)
	)
	val arr = bi.toByteArray().hash256()
	// 1024 - 49151
	var port = bi.mod(BigInteger.valueOf(49151 - 1024)).toInt() + 1024
	while (port in excludedPortSet) {
		++port
	}
	val ipArr = ByteArray(16) {
		when (it) {
			0 -> 0xff.toByte()
			// Link-local scope
			1 -> ((arr[it].toInt() and 0xf0) or 0x02).toByte()
			else -> arr[it]
		}
	}
	return InetSocketAddress(Inet6Address.getByAddress(ipArr), port)
}

fun getIP4MulticastAddress(): InetSocketAddress {
	val bi = prime128 * BigInteger.valueOf(
		47L
				+ GregorianCalendar().get(GregorianCalendar.YEAR)
				* GregorianCalendar().get(GregorianCalendar.DAY_OF_YEAR)
				* GregorianCalendar().get(GregorianCalendar.HOUR_OF_DAY)
	)
	val arr = bi.toByteArray().hash256()
	// 1024 - 49151
	var port = bi.mod(BigInteger.valueOf(49151 - 1024)).toInt() + 1024
	while (port in excludedPortSet) {
		++port
	}
	val ipArr = ByteArray(4) {
		when (it) {
			0 -> 239.toByte()
			else -> arr[it]
		}
	}
	return InetSocketAddress(Inet4Address.getByAddress(ipArr), port)
}

fun getIP4ScanPort(): Int {
	val bi = prime128 * BigInteger.valueOf(
		59L
				+ GregorianCalendar().get(GregorianCalendar.YEAR)
				* GregorianCalendar().get(GregorianCalendar.DAY_OF_YEAR)
				* GregorianCalendar().get(GregorianCalendar.HOUR_OF_DAY)
	)
	// 49152–65535
	return 0xffff - bi.mod(BigInteger.valueOf(16384)).toInt()
}

fun getBroadcastPort(): Int {
	val bi = prime128 * BigInteger.valueOf(
		67L
				+ GregorianCalendar().get(GregorianCalendar.YEAR)
				* GregorianCalendar().get(GregorianCalendar.DAY_OF_YEAR)
				* GregorianCalendar().get(GregorianCalendar.HOUR_OF_DAY)
	)
	// 49152–65535
	return 0xffff - bi.mod(BigInteger.valueOf(16384)).toInt()
}

data class DatagramPacketInfo(val addr: InetSocketAddress?, val length: Int)

fun DatagramPacket.info(): DatagramPacketInfo {
	return DatagramPacketInfo(
		try {
			this.socketAddress as InetSocketAddress
		} catch (e: Exception) {
			log("0Mze2q8EJf: $e")
			null
		}, this.length
	)
}

fun NetworkInterface.getLanP2PAddress(): InetAddress {
	return if (hasIP6()) {
		var addr = getIPAddresses().filter { it.addr.isIP6Anonymous() }.shuffled()
		if (addr.isEmpty()) {
			addr = getIPAddresses().filter { it.addr.isIP6LinkLocal() }.shuffled()
		}
		if (addr.isEmpty()) {
			addr = getIPAddresses().filter { it.addr.isIP6SiteLocal() }.shuffled()
		}
		if (addr.isEmpty()) {
			addr = getIPAddresses().shuffled()
		}
		addr[0].addr
	} else {
		val addr = getIPAddresses().filter { it.addr.isIP4SiteLocal() }.shuffled()
		if (addr.isEmpty()) {
			throw RuntimeException("Unable to find suitable network address")
		}
		addr[0].addr
	}
}

fun createServerSocket(address: InetAddress): ServerSocket {
	val ss = ServerSocket()
	ss.bind(InetSocketAddress(address, 0), 10)
	return ss
}

fun createSocket(address: InetSocketAddress, timeout: Int = 2000): Socket {
	val sock = Socket()
	sock.connect(address, timeout)
	return sock
}

fun ServerSocket.getInetSocketAddress(): InetSocketAddress {
	return localSocketAddress as InetSocketAddress
}

fun NetworkInterface.ip4AddressSize(): Int {
	for (addr in interfaceAddresses) {
		if (addr.address.isIP4()) {
			return (1 shl (32 - addr.networkPrefixLength)) - 1
		}
	}
	return -1
}

fun NetworkInterface.ip4SubnetMask(): Int {
	for (addr in interfaceAddresses) {
		if (addr.address.isIP4()) {
			return 0x7fffffff shl (32 - addr.networkPrefixLength)
		}
	}
	return -1
}

fun compare(a: InetSocketAddress, b: InetSocketAddress): Int {
	var c = Arrays.compare(a.address.address, b.address.address)
	if (c == 0) {
		c = a.port.compareTo(b.port)
	}
	return c
}

fun createMulticastSocket(
	sockAddr: InetSocketAddress,
	inf: NetworkInterface
): MulticastSocket {
	val sock = MulticastSocket(sockAddr.port)
	sock.joinGroup(sockAddr, inf)
	return sock
}

fun NetworkInterface.getScanAddressIterator(): Iterator<InetAddress> {
	val mask = ip4SubnetMask()
	val addrSize = ip4AddressSize()
	val broadcastAddress = getIP4BroadcastAddress()
	val myAddr = createIP4DatagramSocket().use { it.localAddress.address.toInt() }
	fun getAddress(count: Int): InetAddress {
		val addrInt = myAddr and mask or count
		val addr = InetAddress.getByAddress(addrInt.serialize())
		if (addr == broadcastAddress) {
			return getAddress(count + 1)
		}
		return addr
	}
	return object : Iterator<InetAddress> {
		var count = 1
		override fun hasNext(): Boolean {
			return count < addrSize
		}

		override fun next(): InetAddress {
			return getAddress(count++)
		}
	}
}

fun InetAddress.isFE80(): Boolean {
	return address.size > 4 && (address[0].toInt() and 0xFF) == 0xFE && (address[1].toInt() and 0xFF) == 0x80
}

fun InetAddress.isULA(): Boolean {
	return address.size > 4 && ((address[0].toInt() and 0xFF) == 0xFC || (address[0].toInt() and 0xFF) == 0xFD)
}

fun InetAddress.isLinkLocal(): Boolean {
	return isLinkLocalAddress
}

val hexValue = "0123456789abcdef".toCharArray()
fun Byte.asHex(): String {
	val x = this.toInt() and 0xFF
	return String(charArrayOf(hexValue[x ushr 4], hexValue[x and 0xF]))
}

fun InetAddress.toDotAddress(): String {
	val sb = StringBuilder()
	if (address.size > 4) {
		for ((i, x) in this.address.withIndex()) {
			if (i > 0 && i % 2 == 0) {
				sb.append(':')
			}
			sb.append(x.asHex())
		}
	} else {
		for ((i, x) in this.address.withIndex()) {
			if (i > 0) {
				sb.append('.')
			}
			sb.append(x.toInt() and 0xFF)
		}
	}
	return sb.toString()
}

fun DatagramPacket.packetInfo(): String {
	return "Packet - $address:$port $length"
}

fun parseSocketAddress(address: String): InetSocketAddress {
	val addressString = address.trim()
	if (addressString.startsWith("[")) {
		// IPv6 format: [address]:port
		val closingBracketIndex = addressString.indexOf(']')
		if (closingBracketIndex == -1) {
			throw IllegalArgumentException("Invalid IPv6 format: missing closing bracket")
		}
		val ipv6Address = addressString.substring(1, closingBracketIndex)

		if (closingBracketIndex + 1 >= addressString.length || addressString[closingBracketIndex + 1] != ':') {
			throw IllegalArgumentException("Invalid IPv6 format: missing port separator")
		}
		val portString = addressString.substring(closingBracketIndex + 2)
		val port = portString.toIntOrNull()
			?: throw IllegalArgumentException("Invalid port number: $portString")

		if (port !in 0..65535) {
			throw IllegalArgumentException("Port must be between 0 and 65535")
		}

		return InetSocketAddress(ipv6Address, port)
	} else {
		// IPv4 format: address:port
		val lastColonIndex = addressString.lastIndexOf(':')
		if (lastColonIndex == -1) {
			throw IllegalArgumentException("Invalid IPv4 format: missing port separator")
		}
		val ipv4Address = addressString.take(lastColonIndex)
		val portString = addressString.substring(lastColonIndex + 1)
		val port = portString.toIntOrNull()
			?: throw IllegalArgumentException("Invalid port number: $portString")

		if (port !in 0..65535) {
			throw IllegalArgumentException("Port must be between 0 and 65535")
		}

		return InetSocketAddress(ipv4Address, port)
	}
}

fun getAddressListInternal(): List<InetAddress> {
	val list = ArrayList<InetAddress>()
	for (ni in NetworkInterface.getNetworkInterfaces()) {
		if (ni.supportsMulticast()
			&& !ni.isLoopback
			&& ni.isUp
			&& !ni.isVirtual
			&& ni.inetAddresses.toList()
				.isNotEmpty()
		) {
			run {
				val addrList = ni.getIPAddresses().filter { it.addr.isIP6() }
				if (addrList.isNotEmpty()) {
					for (addr in addrList) {
						if (
							!addr.addr.isLoopbackAddress &&
							!addr.addr.isLinkLocal() &&
							!addr.addr.isULA()
						) {
							list.add(addr.addr)
							break
						}
					}
				}
			}
			run {
				val addrList = ni.getIPAddresses().filter { it.addr.isIP4() }
				if (addrList.isNotEmpty()) {
					for (addr in addrList) {
						if (
							!addr.addr.isLoopbackAddress &&
							!addr.addr.isLinkLocal()
						) {
							list.add(addr.addr)
							break
						}
					}
				}
			}
			if (list.size > 10) {
				log("TCPServer warning: Too many addresses found, had to truncate")
				break
			}
		}
	}
	return list
}