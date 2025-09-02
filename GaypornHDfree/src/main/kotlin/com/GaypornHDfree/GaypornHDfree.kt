package com.GaypornHDfree

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
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

class GaypornHDfree : MainAPI() {

    override var name = "GaypornHDfree"
    override var mainUrl = "https://gaypornhdfree.com"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasMainPage = true

    private val standardHeaders: Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5"
    )

    private val fallbackHttpClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    // ---------------- Helpers ----------------
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

    private fun extractStyleImage(style: String?): String? {
        if (style.isNullOrBlank()) return null
        val regex = Regex("""url\((['"]?)(.*?)\1\)""", RegexOption.IGNORE_CASE)
        val m = regex.find(style)
        return m?.groups?.get(2)?.value
    }

    private fun parseSrcsetPickBest(srcset: String): String {
        try {
            val parts = srcset.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) return srcset
            var bestUrl = parts.first()
            var bestWidth = -1
            for (p in parts) {
                val seg = p.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
                val url = seg[0]
                val qualifier = if (seg.size > 1) seg[1] else ""
                val width = qualifier.removeSuffix("w").toIntOrNull() ?: qualifier.removeSuffix("x").toIntOrNull() ?: -1
                if (width > bestWidth) {
                    bestWidth = width
                    bestUrl = url
                }
            }
            return bestUrl
        } catch (e: Exception) {
            return srcset.split(",").firstOrNull()?.trim() ?: srcset
        }
    }

    private fun resolveImgAttr(img: Element): String? {
        val attrs = listOf("data-src", "data-lazy-src", "data-original", "data-srcset", "src", "poster")
        for (a in attrs) {
            val v = img.attr(a)
            if (!v.isNullOrBlank() && !v.contains("placeholder")) {
                if (a.contains("srcset") && v.contains(",")) return parseSrcsetPickBest(v)
                return v
            }
        }
        val style = img.attr("style")
        val fromStyle = extractStyleImage(style)
        if (!fromStyle.isNullOrBlank()) return fromStyle
        return null
    }

    // ---------------- Parse main page content ----------------
    private fun parseMainPageContent(document: Document, requestName: String): HomePageResponse {
        Log.d("GaypornHDfree", "Page title: ${document.title()}")
        if (document.title().isNullOrBlank() || document.html().length < 300) {
            Log.w("GaypornHDfree", "Short HTML/snippet: ${document.html().take(400)}")
        }

        val home = document.select("div.videopost, .video-item, .post-item, .thumb-item, .post, article")
            .mapNotNull { element ->
                try {
                    element.toSearchResult()
                } catch (e: Exception) {
                    Log.e("GaypornHDfree", "Error parsing element: ${e.message}")
                    null
                }
            }

        return newHomePageResponse(
            HomePageList(
                name = requestName,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = home.isNotEmpty()
        )
    }

    // ---------------- Element -> SearchResponse ----------------
    private fun Element.toSearchResult(): SearchResponse? {
        try {
            val titleElement = this.selectFirst("a[href*='/video/']")
                ?: this.selectFirst("a[href*='.html']")
                ?: this.selectFirst("a[title]")
                ?: this.selectFirst("h2 a, h3 a, h4 a")
                ?: this.selectFirst("div.title a")
                ?: this.selectFirst("div.video-title a")
                ?: this.selectFirst("a")

            if (titleElement == null) return null

            val title = titleElement.text().trim().ifEmpty {
                titleElement.attr("title").trim()
            }.ifEmpty {
                titleElement.attr("alt").trim()
            }

            if (title.isEmpty()) return null

            val href = fixUrl(titleElement.attr("href"))
            if (href.isEmpty() || href == mainUrl) return null

            val imgEl = this.selectFirst("img") ?: titleElement.selectFirst("img") ?: this.selectFirst("video")
            var posterRaw: String? = null

            posterRaw = imgEl?.let { resolveImgAttr(it) }

            if (posterRaw.isNullOrBlank()) {
                val srcset = imgEl?.attr("srcset") ?: imgEl?.attr("data-srcset")
                if (!srcset.isNullOrBlank()) posterRaw = parseSrcsetPickBest(srcset)
            }

            if (posterRaw.isNullOrBlank()) {
                val style = imgEl?.attr("style") ?: this.attr("style")
                posterRaw = extractStyleImage(style)
            }

            val poster = posterRaw?.takeIf { it.isNotBlank() }?.let { raw ->
                var r = raw.trim().replace("\\/", "/")
                if (r.startsWith("//")) r = "https:$r"
                if (r.startsWith("http://") || r.startsWith("https://")) r else fixUrl(r)
            } ?: ""

            Log.d("GaypornHDfree", "toSearchResult -> title='$title' href='$href' posterRaw='${posterRaw?.take(120)}' poster='$poster'")

            return newMovieSearchResponse(title, href, TvType.NSFW) {
                if (poster.isNotBlank()) {
                    this.posterUrl = poster
                }
            }
        } catch (e: Exception) {
            Log.e("GaypornHDfree", "toSearchResult error: ${e.message}")
            return null
        }
    }

    // ----------------- getMainPage -----------------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else request.data
        try {
            val docTry = try {
                Jsoup.connect(url).headers(standardHeaders).userAgent(standardHeaders["User-Agent"] ?: "").timeout(15000).get()
            } catch (e: Exception) {
                Jsoup.parse("")
            }

            val suspect = docTry.title().isNullOrBlank() || docTry.html().length < 300 ||
                    docTry.html().contains("challenge-platform") || docTry.html().contains("Ray ID")

            if (!suspect) return parseMainPageContent(docTry, request.name)

            val safe = fetchHtmlSafely(url, mapOf("Referer" to mainUrl))
            if (safe.isNotBlank()) {
                val parsed = Jsoup.parse(safe, url)
                return parseMainPageContent(parsed, request.name)
            }

            return newHomePageResponse(HomePageList(request.name, listOf()), false)
        } catch (e: Exception) {
            Log.e("GaypornHDfree", "getMainPage error: ${e.message}")
            return newHomePageResponse(HomePageList(request.name, listOf()), false)
        }
    }

    // ----------------- search -----------------
    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val seen = mutableSetOf<String>()
        try {
            for (i in 1..3) {
                val q = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "$mainUrl/?s=$q&page=$i"
                val doc = try {
                    Jsoup.connect(searchUrl).headers(standardHeaders).userAgent(standardHeaders["User-Agent"] ?: "").timeout(15000).get()
                } catch (e: Exception) {
                    Jsoup.parse("")
                }
                val pageResults = doc.select("div.videopost, .video-item, .post-item, .thumb-item, article")
                    .mapNotNull { it.toSearchResult() }
                    .filterNot { seen.contains(it.url) }

                if (pageResults.isEmpty()) break
                pageResults.forEach { seen.add(it.url) }
                results.addAll(pageResults)
                delay(400)
            }
        } catch (e: Exception) {
            Log.e("GaypornHDfree", "search error: ${e.message}")
        }
        return results
    }


} 
