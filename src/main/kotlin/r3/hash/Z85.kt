package r3.hash

import r3.util.srnd

//  Maps base 256 to base 85
val encoder = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-:+=^!/*?&<>()[]{}@%$#"
	.toCharArray()

//  Maps base 85 to base 256
//  We chop off lower 32 and higher 128 ranges
val decoder = byteArrayOf(
	0x00, 0x44, 0x00, 0x54, 0x53, 0x52, 0x48, 0x00,
	0x4B, 0x4C, 0x46, 0x41, 0x00, 0x3F, 0x3E, 0x45,
	0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
	0x08, 0x09, 0x40, 0x00, 0x49, 0x42, 0x4A, 0x47,
	0x51, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A,
	0x2B, 0x2C, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32,
	0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A,
	0x3B, 0x3C, 0x3D, 0x4D, 0x00, 0x4E, 0x43, 0x00,
	0x00, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
	0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
	0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20,
	0x21, 0x22, 0x23, 0x4F, 0x00, 0x50, 0x00, 0x00
)

fun padded4(arr: ByteArray): ByteArray {
	val rem = arr.size % 4
	if (rem == 0) {
		return arr
	}
	val pad = ByteArray(4 - rem)
	srnd.nextBytes(pad)
	return arr + pad
}

fun Z85Encode(data: ByteArray): CharArray {
	//  Accepts only byte arrays bounded to 4 bytes
	if (data.size % 4 != 0) {
		throw RuntimeException("Accepts only byte arrays multiple of 4 bytes")
	}
	val encodedSize = data.size * 5 / 4
	val encoded = CharArray(encodedSize)
	var charNum = 0
	var byteNum = 0
	var value = 0L
	while (byteNum < data.size) {
		//  Accumulate value in base 256 (binary)
		value = value * 256 + (data[byteNum++].toInt() and 0xFF)
		if (byteNum % 4 == 0) {
			//  Output value in base 85
			var divisor = 85 * 85 * 85 * 85
			while (divisor != 0) {
				encoded[charNum++] = encoder[(value / divisor % 85).toInt()]
				divisor /= 85
			}
			value = 0
		}
	}
	return encoded
}

fun Z85Decode(chArr: CharArray): ByteArray {
	//  Accepts only strings bounded to 5 bytes
	if (chArr.size % 5 != 0) {
		throw RuntimeException("needs to be a multiple of 5")
	}
	val decoded_size = chArr.size * 4 / 5
	val decoded = ByteArray(decoded_size)
	var byte_nbr = 0
	var char_nbr = 0
	var value = 0L
	while (char_nbr < chArr.size) {
		//  Accumulate value in base 85
		value = value * 85 + decoder[((chArr[char_nbr++].code - 32))]
		if (char_nbr % 5 == 0) {
			//  Output value in base 256
			var divisor = 256 * 256 * 256L
			while (divisor != 0L) {
				decoded[byte_nbr++] = (value / divisor % 256).toByte()
				divisor /= 256
			}
			value = 0
		}
	}
	return decoded
}