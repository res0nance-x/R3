package r3.pke

import r3.encryption.CipherKey
import r3.hash.fromUBase64
import r3.hash.hash
import r3.hash.hash256
import r3.hash.toUBase64
import r3.io.*
import r3.key.Key256
import r3.math.BitMatrix
import r3.util.srnd
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest

val bit127 =
	"AH8AfwAAB-BHC8VMLgFeJfBD6OB_3OusXNEslIT-wFVpkXSWSSZcj1Nknw5twWYTbYTWciXr5uExGtQd8pBRlNum-kwDfp091EOv-8Hle" +
			"y8qFIyQqj7z6d9RLL2qH5A9E2Lqa1_jr8TYnPtC4U2YvtEb0Je-VMjq5YCpKjet_D8SyjvYc0OwlUljDWleN8le5Uk7GoWbZDHKWc0UpUHPIipmS" +
			"SXP8V0HU-tQ7oPdy3obNpuLIwEiVbXfYrgMAAXcTbN63bOlpH6ylxIs3TtEHKP8k2-dHE3oI0YNXrz2iS6yTmN9l2ZN2TNz1Hd1BPDL88nlrcWFn" +
			"gZY3ITQYt9JIyXZ7CGMsfK3hCutEa3OeYQl0A4SICXjswhTIFFTI4aGdapiEn9ojXvXwTnvRH9rYxiodhX_3-glsTAN-X4m7HvSWtZ6368JE8kvu" +
			"dnw6vfyeBWcEozJfMsW666viXyyXpIYU6H2oj5RWVxuiyrBuMnzESKK5dAtb6HBUzUCPAnkSS2-Tswgs7LPajHaWjyeQfEAPEV-3brGI-naySGcC" +
			"BM_AjISNF1uVjWdgZTLMuhxgbeq8TB2mnw4mX1juP9sxs1MXyw7olMb7rBrPrqO95v-2EVnNdtcJMiYev0r_XMWhwiMw5lxQIsvLn-T5Q671FmTe" +
			"ilqovZHgoCbVYh0hkH2IJxhuXGVbAuUJ9l9LID3IptgWytrRG-LZhQQtbF1ecW74hz0mDSkaupdORddE1pKGF62sJIQrOyfdshatjAK-OTQ8AgPY" +
			"dgsjJjfPov1wPSM7_5cZyQDExIQP2itfe6vMdkdOS_dzoztB0hgrdHFkAQPqDbKK0RSke79jI0POHSvKLNJV2RFQMt1ofgN-gCEgJjh-D3QDghU8" +
			"VBfeIYRLGRflSW9rfiViBHJVj4pjFLWXVakVwRWwYQiOw_kvFNyccezU460Rz468yw651VMUjiV62_fCvExzDXwcLs13P7HOCGy7FCJOufHSp3Dd" +
			"pUxcs6v9_Xua5IMVJ--OxIE2BegNKcMZd_2y687Y6DNK-8Ik9w0g_KqBlj0WQMrMjrt6Q3wfyb87y4THoVQtqPaRRKKHrR2vKp2QwVUQha2Nsga0" +
			"8oJFG-z1BFo7GHcQxZYrl3_-80QhcG8nDAAa_4rIZqJ_s2_R0XqWZOrX7vdBVveAAJjG6cuW2A_H4z-pmxa9sgiOP4kqxHSzM4sBuGY6LSGWdiOZ" +
			"tDdk5cotR23Ce0moDiTLAMGju-t4h5Ghb2EHCFAafiybn_iIuswXwtAIHJq6wyvQFk5sps5mOymEMabmMQ5cbuh-8ZIebhLBxTzREKqvbycSY5iT" +
			"JKZAy5aFlgWpOT9LF4q5Ey65OCw_3mnrD1c4HiV6OM95KG9FgP9KvcGHsUDUEJOYqlbBD7jAm0Jx_UHC5Jm6p1xWYnw3PtaZEtkksW2vWbuNElVh" +
			"X57OXxZx2NwGzkuYGPfARvhs5_x7nrqunoKO8KA-h1ZkgcqFNxACEToy8A5fTa-oBC1Q5H5GvH7huZAjXpBuXuU0SGGPs6ObqWivdiJG7r7Jgyg3" +
			"vVz931zXS_BhzkxmoyitF6Gud3zeoW2p4uJrlQqfBJSQgwcYwmoAr9G-SW8O080kG8bo2E_QSZch4RiIi6x4apLNsOx6DLM_oL3GLVn8XM-k2w7l" +
			"dvmQH8D-SFYCNwJnI9JAyJcyhINbP_Q4Vr5jx8PvKS3SgtUjug6Q7NnwAo5Xrla4RSerYCeXJ3Iky7ExxDwJ7kiFx5_9Zo4SnZcmjX45Byhv0Zca" +
			"FnzVyRdd4X-wBp5crC4ljK9iQPe6kaHuzFFa86cztXPdnDy8jrMzQNZwVD8WSdp7YEu8AxiHvPK8LvrJ0BQxB-5izWil4gpOaqiSZI1nvK1MM-Lq" +
			"XRFUXMeDlzmoREyXYKMw-oLBDQZP4_q9UYiE9tViUxYgchortcWlVWjxCxs0ynLWOH_0BN53mvourm3xIUsPBOj-o9fYnGXWbaYsaeSjBOuBX1OA" +
			"f-2e-huTQ2SBXiCos8PTUWXp5Y-MocO4SRQfI43di2fb-yvrqqb-7ssBxFkaRyk6rbfWQIcZpz_QyCGihy-qvfc4mklhytgI8vYOlW9f2gPWKFSO" +
			"1IEybjrurNOeX8TIx2WOjY_rmt8tI3SwPuFSNzRCP88iw-u2GhTkQ_Ffj5QiZFXyY4W1C502RsURxkmnmFI9XwI_gxs-BY7Ag_lUWLppz-BooiLv" +
			"kaEpgwFahEfyFIMHtYXVbx3i9BnRNE6ZeSPc3jLvchMbrQdbqehZqgsYSfrtg8D_39ZqIuhfmxCn6b24kq3J9CMtVj5NJobtDNpxqBShbz3bqluO" +
			"Kba2wDpUy_cFs5FGFV9Pt1W8TxeHzy45THUWhGa2GOj3oOxqXSLPRWHXbyAQZm0dxaKI8eBZ3OOXujzpLE1-OS8tmK1eHa1PhONF347eZPe03VU_" +
			"oBhZ3ZXb-b2YeV65sCM3pEZAeOgNjuFQ3aN3ctYIZzhKOBXWmpiKRWHJvPYrVsyCrMtR_IMwOspK7t0mTCaNJ-bNwYYEf1MrWb_jKfBf3wKJUwf6" +
			"b4WDBvRNzVX-WPSerhWbPe9_Wz06wyYFd2Z7RBFooMpw4OfcWt598ycIIAtyTWLhjgCoCBFBNHy5q6s510u4jCHFrS34bHBsBfthVl70LsuNWxRm" +
			"dMdyHwc5QonDhDsbr4="

class BitIdentity : Writable {
	val m = BitMatrix.read(bit127.fromUBase64().toDataInputStream())
	val x: BigInteger
	val mx: BitMatrix
	val primeBits = 127
	val prime = BigInteger.valueOf(2).pow(127).minus(BigInteger.ONE)
	val key: Key256
		get() = Key256(mx.serialize().hash256())

	constructor() {
		x = BigInteger(1, srnd.getByteArray(primeBits))
		mx = m.mpow(x)
	}

	constructor(x: BigInteger) {
		this.x = x
		mx = m.mpow(x)
	}

	fun sign(istream: InputStream): Signature {
		val md = MessageDigest.getInstance("SHA-256")
		val ip = prime - BigInteger.ONE
		val k = BigInteger(1, srnd.getByteArray(primeBits))
		val r = m.mpow(k)
		md.update(r.serialize())
		val h = BigInteger(1, hash(md, istream))
		val mxh = ip - (x * h).mod(ip)
		val s = (k + mxh).mod(ip)
		return Signature(h, s)
	}

	fun sign(arr: ByteArray): Signature {
		return sign(ByteArrayInputStream(arr))
	}

	fun asPeer(): BitPeer {
		return BitPeer(mx)
	}

	fun createCipherKey(my: BitMatrix): CipherKey {
		val mxy = my.mpow(x)
		return r3.encryption.createCipherKey(mxy)
	}

	override fun write(dos: DataOutputStream) {
		x.write(dos)
	}

	companion object {
		fun read(dis: DataInputStream): BitIdentity {
			val x = readBigInteger(dis)
			return BitIdentity(x)
		}
	}

	override fun toString(): String {
		return "BitIdentity: " + key.serialize().toUBase64()
	}
}
