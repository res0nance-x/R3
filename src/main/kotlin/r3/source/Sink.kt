package r3.source

import java.io.OutputStream

interface Sink {
	fun createOutputStream(): OutputStream
}