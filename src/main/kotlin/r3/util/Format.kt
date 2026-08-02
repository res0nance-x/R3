package r3.util

fun Long.humanReadableSize(): String {
	if (this < 1000) return "$this B"
	val units = arrayOf("KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
	var value = this.toDouble()
	var idx = -1
	while (value >= 1000 && idx < units.size - 1) {
		value /= 1000.0
		idx++
	}
	// If idx == -1 it means value was <1024 which shouldn't happen due to earlier guard
	val unit = if (idx >= 0) units[idx] else "B"
	// Show one decimal place for values < 10, otherwise no decimals
	val formatted = if (value < 10) String.format("%.1f", value) else String.format("%.0f", value)
	return "$formatted $unit"
}

fun Long.dateTime(): String {
	val dt = java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault())
	return dt.toLocalDateTime().toString()
}