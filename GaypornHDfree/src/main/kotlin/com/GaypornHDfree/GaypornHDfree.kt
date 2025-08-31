package com.GaypornHDfree

import android.util.Log
import com.lagradost.cloudstream3.*
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.net.URLEncoder

/*
  GaypornHDfree.kt (fixed, brackets balanced)
  - Đã loại các tham chiếu không tồn tại
  - Thêm fetchHtmlSafely, fixUrl
  - Không có hàm/override ở top-level
*/

class GaypornHDfree : MainAPI() {

    // Basic info (use var in case base class expects var)
    override var name = "GaypornHDfree"
    override var mainUrl = "https://gaypornhdfree.com"
    override var lang = "vi"
    override val supportedTypes = setOf(TvType.NSFW)
    val version = "1.0"

    // Local standard headers
    private val standardHeaders: Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5"
    )

    // OkHttp client for safe fetches
    private val fallbackHttpClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    // Safe fetcher: try decode gzip, avoid brotli by requesting gzip,deflate
    private fun fetchHtmlSafely(url: String, extraHeaders: Map<String, String> = mapOf()): String {
        try {
            val headersMap = mutableMapOf<String, String>()
            headersMap.putAll(standardHeaders)
            headersMap.putAll(extraHeaders)
            headersMap["Accept-Encoding"] = "gzip, deflate"
            val headers: Headers = headersMap.toHeaders()

            val req = Request.Builder()
                .url(url)
                .headers(headers)
                .get()
                .build()

            fallbackHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body ?: return ""
                val bytes = body.bytes()
                val contentEncoding = resp.header("Content-Encoding")?.lowercase()
                val isGzip = contentEncoding == "gzip" ||
                        (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())

                if (isGzip) {
                    return try {
                        GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } catch (_: Exception) {
                        try { String(bytes, Charsets.UTF_8) } catch (_: Exception) { "" }
                    }
                } else {
                    val text = try { String(bytes, Charsets.UTF_8) } catch (_: Exception) { "" }
                    val nonPrintable = text.count { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
                    if (text.isBlank() || (text.length > 0 && nonPrintable.toDouble() / text.length > 0.30)) {
                        return ""
                    }
                    return text
                }
            }
        } catch (e: Exception) {
            Log.w("GaypornHDfree", "fetchHtmlSafely failed for $url: ${e.message}")
            return ""
        }
    }

    // Resolve relative URLs safely
    private fun fixUrl(href: String?): String {
        if (href == null) return ""
        val u = href.trim()
        if (u.isEmpty()) return ""
        if (u.startsWith("http://") || u.startsWith("https://")) return u
        if (u.startsWith("//")) return "https:$u"
        if (u.startsWith("/")) {
            val base = mainUrl.trimEnd('/')
            return base + u
        }
        val base = mainUrl.trimEnd('/')
        return "$base/$u"
    }

    // ----------------- Main page -----------------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else "${request.data}"

        try {
            val document = try {
                Jsoup.connect(url)
                    .headers(standardHeaders)
                    .userAgent(standardHeaders["User-Agent"] ?: "")
                    .timeout(15_000)
                    .get()
            } catch (e: Exception) {
                Log.w("GaypornHDfree", "Jsoup primary fetch failed for $url: ${e.message}")
                Jsoup.parse("")
            }

            val suspect = document.title().isNullOrBlank() ||
                    document.html().length < 300 ||
                    document.html().contains("challenge-platform") ||
                    document.html().contains("Ray ID")

            if (suspect) {
                Log.w("GaypornHDfree", "Suspect HTML detected for $url. Snippet: ${document.html().take(300)}")

                val safeHtml = fetchHtmlSafely(url, mapOf("Referer" to mainUrl, "Cookie" to "hasVisited=1"))
                if (safeHtml.isNotBlank() && !safeHtml.contains("challenge-platform") && !safeHtml.contains("Ray ID")) {
                    val parsed = Jsoup.parse(safeHtml, url)
                    return parseMainPageContent(parsed, request.name)
                }

                val altHtml = fetchHtmlSafely(url, mapOf("Referer" to mainUrl, "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64)"))
                if (altHtml.isNotBlank() && !altHtml.contains("challenge-platform") && !altHtml.contains("Ray ID")) {
                    val parsedAlt = Jsoup.parse(altHtml, url)
                    return parseMainPageContent(parsedAlt, request.name)
                }

                Log.e("GaypornHDfree", "Cloudflare/unrenderable page for $url - returning empty")
                return newHomePageResponse(HomePageList(request.name, listOf()), false)
            } else {
                return parseMainPageContent(document, request.name)
            }
        } catch (e: Exception) {
            Log.e("GaypornHDfree", "Error in getMainPage: ${e.message}")
            return newHomePageResponse(HomePageList(request.name, listOf()), false)
        }
    }

    // -----------
