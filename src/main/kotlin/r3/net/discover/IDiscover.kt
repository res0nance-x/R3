package r3.net.discover

interface IDiscover {
	fun discover(found: (PeerAddressInfo) -> Unit)
}