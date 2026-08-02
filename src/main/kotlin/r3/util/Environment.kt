package r3.util

import java.util.*

fun isAndroid(): Boolean {
	return (System.getProperty("java.specification.vendor") ?: "").lowercase(Locale.getDefault()).contains("android")
}