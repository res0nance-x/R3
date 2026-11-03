package r3.pack

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import r3.content.getMimeType
import r3.http.HandlerFactory
import r3.http.WebServer
import r3.io.log
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Test tool and viewer for MHTML files.
 *
 * Scans test MHTML archives in `D:\data\mht_tests` (or custom locations), parses them with [MhtmlPack],
 * validates loose path resolution with [MHTPackMap], tests local HTTP serving via [WebServer],
 * and logs detailed diagnostics for missing links, loose matches, and MIME type assignments.
 */
class MhtmlTestViewer {

	data class AnalysisReport(
		val file: File,
		val title: String?,
		val snapshotLocation: String?,
		val date: String?,
		val entryCount: Int,
		val htmlEntryFound: Boolean,
		val totalEmbeddedRefs: Int,
		val exactMatches: Int,
		val looseMatches: Int,
		val looseWarnings: List<String>,
		val missingAssets: List<MissingAssetInfo>,
		val mimeTypeMismatches: List<String>,
		val httpServingSuccess: Boolean
	)

	data class MissingAssetInfo(
		val tag: String,
		val url: String,
		val isLikelyExternalOrAd: Boolean
	)

	@Test
	fun testViewAndAnalyzeMhtmlFiles() {
		val testDir = File("""D:\data\mht_tests""")
		if (!testDir.exists()) {
			println("Test directory D:\\data\\mht_tests does not exist. Skipping integration test.")
			return
		}

		val files = testDir.listFiles { _, name ->
			name.endsWith(".mhtml", ignoreCase = true) || name.endsWith(".mht", ignoreCase = true)
		}?.sortedBy { it.name } ?: return

		println("\n" + "=".repeat(75))
		println("MHTML ARCHIVE ANALYSIS AND VIEW TEST REPORT")
		println("Found ${files.size} test archive(s) in ${testDir.absolutePath}")
		println("=".repeat(75))

		val reports = ArrayList<AnalysisReport>()

		for (file in files) {
			val report = analyzeFile(file)
			reports.add(report)
			printReport(report)
		}

		// Summary assertions
		for (report in reports) {
			assertTrue(report.entryCount > 0, "${report.file.name} must have entries")
			assertTrue(report.htmlEntryFound, "${report.file.name} must contain an HTML entry")
			assertTrue(report.httpServingSuccess, "HTTP server must successfully serve index.html for ${report.file.name}")
		}
	}

	fun analyzeFile(file: File): AnalysisReport {
		val looseWarnings = ArrayList<String>()
		val origLog = log
		log = { msg ->
			if (msg.startsWith("MHTPackMap WARNING")) {
				looseWarnings.add(msg)
			} else {
				origLog(msg)
			}
		}

		try {
			val pack = MhtmlPack(file, rewriteLinks = true)
			val packMap = pack.packMap

			val index = pack["index.html"] ?: pack.firstOrNull { it.path == "index.html" || it.ext == "html" }
			val htmlEntryFound = (index != null)

			var totalEmbeddedRefs = 0
			var exactMatches = 0
			var looseMatches = 0
			val missingAssets = ArrayList<MissingAssetInfo>()
			val mimeTypeMismatches = ArrayList<String>()

			// Check MIME types from MHTML headers
			for (content in pack) {
				val headerMime = content.mimeType
				if (headerMime.isNullOrBlank()) {
					mimeTypeMismatches.add("${content.path} has no MIME type from MHTML entry")
				}
			}

			if (index != null) {
				val html = index.createInputStream().readAllBytes().toString(Charsets.UTF_8)
				val embeddedRefs = extractEmbeddedReferences(html)
				totalEmbeddedRefs = embeddedRefs.size

				for ((tag, url) in embeddedRefs) {
					if (url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("#")) {
						continue
					}

					val beforeWarningsSize = looseWarnings.size
					val resolved = packMap[url]
					val isLoose = looseWarnings.size > beforeWarningsSize

					if (resolved != null) {
						if (isLoose) {
							looseMatches++
						} else {
							exactMatches++
						}
					} else {
						val isExternalOrAd = isThirdPartyOrAdUrl(url)
						missingAssets.add(MissingAssetInfo(tag, url, isExternalOrAd))
					}
				}
			}

			// Test HTTP WebServer serving
			var httpSuccess = false
			val tmpDir = File(System.getProperty("java.io.tmpdir"), "mhtml_test_server_${System.nanoTime()}")
			tmpDir.mkdirs()
			try {
				val ws = WebServer("localhost", 0, tmpDir)
				ws.handlers.add(HandlerFactory.createWelcomeHandler())
				ws.handlers.add(HandlerFactory.createPackHandler(pack))
				ws.start(0, false)

				val port = ws.listeningPort
				if (port > 0) {
					val url = URI("http://localhost:$port/index.html").toURL()
					val conn = url.openConnection() as HttpURLConnection
					conn.connectTimeout = 3000
					conn.readTimeout = 3000
					conn.requestMethod = "GET"
					val code = conn.responseCode
					val contentType = conn.contentType
					val length = conn.contentLengthLong
					println("  [HTTP Debug] port=$port code=$code contentType=$contentType length=$length")
					if (code == 200 && contentType != null) {
						httpSuccess = true
					}
					conn.disconnect()
				}
				ws.stop()
			} catch (e: Exception) {
				println("  WebServer error: ${e.message}")
			} finally {
				tmpDir.deleteRecursively()
			}

			return AnalysisReport(
				file = file,
				title = pack.title,
				snapshotLocation = pack.snapshotLocation,
				date = pack.date,
				entryCount = pack.size,
				htmlEntryFound = htmlEntryFound,
				totalEmbeddedRefs = totalEmbeddedRefs,
				exactMatches = exactMatches,
				looseMatches = looseMatches,
				looseWarnings = looseWarnings,
				missingAssets = missingAssets,
				mimeTypeMismatches = mimeTypeMismatches,
				httpServingSuccess = httpSuccess
			)
		} finally {
			log = origLog
		}
	}

	private fun printReport(report: AnalysisReport) {
		println("\n" + "-".repeat(75))
		println("FILE: ${report.file.name} (${report.file.length()} bytes)")
		println("  Title: ${report.title ?: "(none)"}")
		println("  Snapshot Location: ${report.snapshotLocation ?: "(none)"}")
		println("  Date: ${report.date ?: "(none)"}")
		println("  MHTML Archive Parts: ${report.entryCount}")
		println("  Primary HTML Detected: ${report.htmlEntryFound}")
		println("  HTTP Serving Test: ${if (report.httpServingSuccess) "SUCCESS (200 OK)" else "FAILED"}")
		println("  Embedded Asset References: ${report.totalEmbeddedRefs}")
		println("    Exact Matches: ${report.exactMatches}")
		println("    Loose Matches: ${report.looseMatches}")
		println("    Unresolved References: ${report.missingAssets.size}")

		val genuineMissing = report.missingAssets.filter { !it.isLikelyExternalOrAd }
		val externalAdMissing = report.missingAssets.filter { it.isLikelyExternalOrAd }

		if (genuineMissing.isNotEmpty()) {
			println("  WARNING: Potentially missing local assets (${genuineMissing.size}):")
			genuineMissing.take(10).forEach {
				println("    [${it.tag}] ${it.url}")
			}
			if (genuineMissing.size > 10) println("    ... and ${genuineMissing.size - 10} more")
		} else {
			println("  All local / internal assets successfully resolved!")
		}

		if (externalAdMissing.isNotEmpty()) {
			println("  External navigation / un-saved ad networks (${externalAdMissing.size}):")
			externalAdMissing.take(5).forEach {
				println("    [${it.tag}] ${it.url}")
			}
			if (externalAdMissing.size > 5) println("    ... and ${externalAdMissing.size - 5} more")
		}

		if (report.looseWarnings.isNotEmpty()) {
			println("  Sample Loose Matching Warnings (${report.looseWarnings.size} total):")
			report.looseWarnings.take(6).forEach {
				println("    $it")
			}
			if (report.looseWarnings.size > 6) println("    ... and ${report.looseWarnings.size - 6} more")
		}
	}

	private fun extractEmbeddedReferences(html: String): List<Pair<String, String>> {
		val results = ArrayList<Pair<String, String>>()

		// img src
		Regex("""(?i)<img\b[^>]*?\bsrc=["']([^"']+)["']""").findAll(html)
			.forEach { results.add(Pair("img.src", it.groupValues[1])) }

		// stylesheet link
		Regex("""(?i)<link\b[^>]*?rel=["']stylesheet["'][^>]*?\bhref=["']([^"']+)["']""").findAll(html)
			.forEach { results.add(Pair("link.css", it.groupValues[1])) }

		// script src
		Regex("""(?i)<script\b[^>]*?\bsrc=["']([^"']+)["']""").findAll(html)
			.forEach { results.add(Pair("script.src", it.groupValues[1])) }

		// video/audio src & poster
		Regex("""(?i)<(?:video|audio)\b[^>]*?\bsrc=["']([^"']+)["']""").findAll(html)
			.forEach { results.add(Pair("media.src", it.groupValues[1])) }
		Regex("""(?i)<video\b[^>]*?\bposter=["']([^"']+)["']""").findAll(html)
			.forEach { results.add(Pair("video.poster", it.groupValues[1])) }

		// source src & srcset
		Regex("""(?i)<source\b[^>]*?\bsrc=["']([^"']+)["']""").findAll(html)
			.forEach { results.add(Pair("source.src", it.groupValues[1])) }
		Regex("""(?i)\bsrcset=["']([^"']+)["']""").findAll(html).forEach { match ->
			match.groupValues[1].split(',').forEach { item ->
				val u = item.trim().substringBefore(' ')
				if (u.isNotEmpty()) results.add(Pair("srcset", u))
			}
		}

		// CSS url(...)
		Regex("""(?i)url\(\s*["']?([^"')]+)["']?\s*\)""").findAll(html)
			.forEach { results.add(Pair("css.url", it.groupValues[1])) }

		return results
	}

	private fun isThirdPartyOrAdUrl(url: String): Boolean {
		val u = url.lowercase()
		return u.contains("doubleclick.net") ||
			u.contains("google-analytics") ||
			u.contains("googlesyndication") ||
			u.contains("googletagservices") ||
			u.contains("amazon-adsystem") ||
			u.contains("scorecardresearch") ||
			u.contains("crwdcntrl.net") ||
			u.contains("permutive") ||
			u.contains("adobedtm") ||
			u.contains("parsely.com") ||
			u.contains("fonts.googleapis.com") ||
			u.contains("fonts.gstatic.com") ||
			u.contains("gravatar.com") ||
			u.contains("facebook.com") ||
			u.contains("twitter.com") ||
			u.contains("linkedin.com")
	}

	companion object {
		/**
		 * Launches a local WebServer to view an MHTML file in the default browser.
		 * Usage: pass path to .mhtml as first argument.
		 */
		@JvmStatic
		fun main(args: Array<String>) {
			val path = if (args.isNotEmpty()) args[0] else """D:\data\mht_tests\CBC_News.mhtml"""
			val file = File(path)
			if (!file.exists()) {
				println("File not found: ${file.absolutePath}")
				return
			}

			println("Loading MHTML: ${file.name}...")
			val pack = MhtmlPack(file, rewriteLinks = true)
			val tmpDir = File(System.getProperty("java.io.tmpdir"), "mhtml_viewer_tmp")
			tmpDir.mkdirs()

			val ws = WebServer("localhost", 0, tmpDir)
			ws.handlers.add(HandlerFactory.createWelcomeHandler())
			ws.handlers.add(HandlerFactory.createPackHandler(pack))
			ws.start(0, false)

			val port = ws.listeningPort
			val url = "http://localhost:$port/index.html"
			println("\nPack server is running for '${pack.title}'!")
			println("Open in browser: $url")

			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				try {
					Desktop.getDesktop().browse(URI(url))
				} catch (e: Exception) {
					println("Could not open browser automatically: ${e.message}")
				}
			}

			println("\nPress [ENTER] to stop server...")
			System.`in`.read()
			ws.stop()
			println("Server stopped.")
		}
	}
}
