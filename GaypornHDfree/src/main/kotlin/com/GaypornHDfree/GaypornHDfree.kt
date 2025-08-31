package com.GaypornHDfree

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
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

/**
 * GaypornHDfree.kt — cleaned & braces-balanced
 * - Helpers first, then parseMainPageContent, then other handlers.
 * - Avoids references to unavailable APIs.
 */

class GaypornHDfree : MainAPI() {

    // Basic info (use var in case base expects var)
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

    // ---------- Helpers ----------
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

    // ---------- Parse main page content (placed early) ----------
    private fun parseMainPageContent(document: Document, requestName: String): HomePageResponse {
        Log.d("GaypornHDfree", "Page title: ${document.title()}")
        if (document.title().isNullOrBlank() || document.html().length < 300) {
            Log.w("GaypornHDfree", "Short HTML/snippet: ${document.html().take(800)}")
        }

        val home = document.select("div.videopost, .video-item, .post-item, .thumb-item, div[class*='video'], article, .post")
            .mapNotNull { element ->
                try {
                    element.toSearchResult()
                } catch (e: Exception) {
                    Log.e("GaypornHDfree", "Error parsing search result: ${e.message}")
                    null
                }
            }

        return newHomePageResponse(
            list = HomePageList(
                name = requestName,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = home.isNotEmpty()
        )
    }

    // ---------- Element -> SearchResult ----------
    private fun Element.toSearchResult(): SearchResponse? {
        return try {
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

            val img = this.selectFirst("img")
                ?: this.selectFirst("video")
                ?: titleElement.selectFirst("img")

            val poster = img?.let { imgEl ->
                val attrs = listOf("data-src", "data-lazy-src", "data-original", "src", "poster")
                attrs.map { attr -> imgEl.attr(attr) }
                    .firstOrNull { it.isNotEmpty() && !it.contains("placeholder") }
            } ?: ""

            return newMovieSearchResponse(title, href, TvType.NSFW) {
                if (poster.isNotBlank()) {
                    this.posterUrl = when {
                        poster.startsWith("http") -> poster
                        poster.startsWith("//") -> "https:$poster"
                        poster.startsWith("/") -> "$mainUrl$poster"
                        else -> "$mainUrl/$poster"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GaypornHDfree", "Error in toSearchResult: ${e.message}")
            null
        }
    }

    // ---------- Main page (uses parseMainPageContent above) ----------
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

    // ---------- Search ----------
    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        val seenUrls = mutableSetOf<String>()

        try {
            for (i in 1..3) {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "$mainUrl/?s=$encodedQuery&page=$i"

                val document = try {
                    Jsoup.connect(searchUrl)
                        .headers(standardHeaders)
                        .userAgent(standardHeaders["User-Agent"] ?: "")
                        .timeout(15000)
                        .get()
                } catch (e: Exception) {
                    Log.w("GaypornHDfree", "Search fetch failed for $searchUrl: ${e.message}")
                    Jsoup.parse("")
                }

                val results = document.select("div.videopost, .video-item, .post-item, .thumb-item, div[class*='video'], article")
                    .mapNotNull {
                        try {
                            it.toSearchResult()
                        } catch (e: Exception) {
                            Log.e("GaypornHDfree", "Error parsing search result: ${e.message}")
                            null
                        }
                    }.filterNot { seenUrls.contains(it.url) }

                if (results.isEmpty()) break

                results.forEach { seenUrls.add(it.url) }
                searchResponse.addAll(results)

                delay(500)
            }
        } catch (e: Exception) {
            Log.e("GaypornHDfree", "Error in search: ${e.message}")
        }

        return searchResponse
    }

    // ---------- Load ----------
    override suspend fun load(url: String): LoadResponse {
        try {
            val document = try {
                Jsoup.connect(url)
                    .headers(standardHeaders)
                    .userAgent(standardHeaders["User-Agent"] ?: "")
                    .timeout(15_000)
                    .get()
            } catch (e: Exception) {
                Log.w("GaypornHDfree", "Jsoup load failed for $url: ${e.message}")
                Jsoup.parse("")
            }

            if (document.title().isNullOrBlank() || document.html().contains("challenge-platform") || document.html().length < 300) {
                Log.w("GaypornHDfree", "Suspect HTML on load for $url. Snippet: ${document.html().take(800)}")

                try {
                    delay(2000)
                    val retryDoc = Jsoup.connect(url).headers(standardHeaders).userAgent(standardHeaders["User-Agent"] ?: "").timeout(15000).get()
                    if (!retryDoc.title().isNullOrBlank() && !retryDoc.html().contains("challenge-platform")) {
                        return parseLoadResponse(retryDoc, url)
                    }
                    Log.w("GaypornHDfree", "Retry returned challenge/empty on load for $url")
                } catch (e: Exception) {
                    Log.w("GaypornHDfree", "Retry failed on load: ${e.message}")
                }

                val safeHtml = fetchHtmlSafely(url, mapOf("Referer" to mainUrl))
                if (safeHtml.isNotBlank() && !safeHtml.contains("challenge-platform") && !safeHtml.contains("Ray ID")) {
                    val doc = Jsoup.parse(safeHtml, url)
                    return parseLoadResponse(doc, url)
                }

                val altHtml = fetchHtmlSafely(url, mapOf("User-Agent" to "Mozilla/5.0 (X11; Linux x86_64)"))
                if (altHtml.isNotBlank() && !altHtml.contains("challenge-platform") && !altHtml.contains("Ray ID")) {
                    val docAlt = Jsoup.parse(altHtml, url)
                    return parseLoadResponse(docAlt, url)
                }

                throw Exception("Cloudflare protection active or page unrenderable for $url")
            } else {
                return parseLoadResponse(document, url)
            }
        } catch (e: Exception) {
            Log.e("GaypornHDfree", "Error in load: ${e.message}")
            throw e
        }
    }

    // ---------- parseLoadResponse ----------
    private suspend fun parseLoadResponse(document: Document, url: String): LoadResponse {
        val title = listOf(
            document.selectFirst("meta[property='og:title']")?.attr("content"),
            document.selectFirst("title")?.text()?.replace(" - GayPornHDfree", ""),
            document.selectFirst("h1")?.text(),
            document.selectFirst(".video-title, .entry-title")?.text()
        ).firstOrNull { !it.isNullOrBlank() }?.trim() ?: "Unknown Title"

        val poster = listOf(
            document.selectFirst("meta[property='og:image']")?.attr("content"),
            document.selectFirst("video")?.attr("poster"),
            document.selectFirst(".video-thumb img, .wp-post-image")?.attr("src")
        ).firstOrNull { !it.isNullOrBlank() } ?: ""

        val description = listOf(
            document.selectFirst("meta[property='og:description']")?.attr("content"),
            document.selectFirst("meta[name='description']")?.attr("content"),
            document.selectFirst(".video-description, .entry-content p")?.text()
        ).firstOrNull { !it.isNullOrBlank() }?.trim() ?: ""

        val recommendations = document.select("div.videopost, .related-video, .video-item, .post").take(10).mapNotNull {
            try {
                it.toSearchResult()
            } catch (e: Exception) {
                Log.e("GaypornHDfree", "Error parsing recommendation: ${e.message}")
                null
            }
        }

        Log.d("GaypornHDfree", "Loaded: title='$title', poster='$poster'")

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            if (poster.isNotBlank()) {
                this.posterUrl = when {
                    poster.startsWith("http") -> poster
                    poster.startsWith("//") -> "https:$poster"
                    poster.startsWith("/") -> "$mainUrl$poster"
                    else -> "$mainUrl/$poster"
                }
            }
            this.plot = description
            this.recommendations = recommendations
        }
    }

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    return try {
        val headers = standardHeaders + mapOf("Referer" to data)
        // 1) Lấy HTML (ưu tiên fetchHtmlSafely nếu có)
        val html = fetchHtmlSafely(data, mapOf("Referer" to mainUrl)).ifEmpty {
            try {
                Jsoup.connect(data).headers(standardHeaders).userAgent(standardHeaders["User-Agent"] ?: "").timeout(15_000).get().html()
            } catch (e: Exception) { "" }
        }
        if (html.isBlank()) {
            Log.w("GaypornHDfree", "loadLinks: empty html for $data")
            return false
        }

        val doc = Jsoup.parse(html, data)
        val videoUrls = mutableSetOf<String>()

        // 2) <video> / <source>
        doc.select("video").forEach { v ->
            val src = v.attr("src")
            if (src.isNotBlank()) videoUrls.add(fixUrl(src))
            v.select("source").forEach { s ->
                val ss = s.attr("src")
                if (ss.isNotBlank()) videoUrls.add(fixUrl(ss))
            }
        }

        // 3) meta og:video, og:video:secure_url
        doc.select("meta[property=og:video], meta[property=og:video:secure_url], meta[name=og:video]")
            .forEach { m ->
                val c = m.attr("content")
                if (c.isNotBlank()) videoUrls.add(fixUrl(c))
            }

        // 4) data-src on player wrappers (ví dụ: <div class="video-player" data-src="...">)
        doc.select("[data-src]").forEach { el ->
            val ds = el.attr("data-src")
            if (ds.isNotBlank()) videoUrls.add(fixUrl(ds))
        }

        // 5) iframe embeds
        doc.select("iframe[src]").forEach { ifr ->
            val src = ifr.attr("src")
            if (src.isNotBlank()) {
                val abs = fixUrl(src)
                videoUrls.add(abs)
            }
        }

        // 6) search inside <script> for file/src/url strings / jwplayer or sources arrays
        val scriptsCombined = doc.select("script").map { it.data() + it.html() }.joinToString("\n")
        // regex: file: "https://..."
        val fileRegex = Regex("""(?i)(?:file|src|url)\s*[:=]\s*['"]((?:https?:)?\/\/[^'"]+)['"]""")
        fileRegex.findAll(scriptsCombined).forEach { m ->
            val u = m.groups[1]?.value ?: ""
            if (u.isNotBlank()) videoUrls.add(fixUrl(u.replace("\\/", "/")))
        }
        // fallback: direct http links to typical video extensions
        val genericRegex = Regex("""https?:\/\/[^\s'"]+?\.(mp4|m3u8|webm|mpd)(\?[^\s'"]*)?""", RegexOption.IGNORE_CASE)
        genericRegex.findAll(html).forEach { m -> videoUrls.add(m.value.replace("\\/", "/")) }

        // 7) Nếu iframe points to external embed page, try to fetch minimal iframe page and extract
        val iframeCandidates = videoUrls.filter { it.contains("embed") || it.contains("/e/") || it.contains("player") }.toList()
        for (ifr in iframeCandidates) {
            try {
                val ifHtml = fetchHtmlSafely(ifr, mapOf("Referer" to data)).ifEmpty {
                    try {
                        Jsoup.connect(ifr).headers(standardHeaders).userAgent(standardHeaders["User-Agent"] ?: "").timeout(8000).get().html()
                    } catch (_: Exception) { "" }
                }
                if (ifHtml.isNotBlank()) {
                    // extract direct links inside iframe content
                    genericRegex.findAll(ifHtml).forEach { m -> videoUrls.add(m.value.replace("\\/", "/")) }
                    val scriptblob = Jsoup.parse(ifHtml).select("script").map { it.data() + it.html() }.joinToString("\n")
                    fileRegex.findAll(scriptblob).forEach { m ->
                        val u = m.groups[1]?.value ?: ""
                        if (u.isNotBlank()) videoUrls.add(fixUrl(u.replace("\\/", "/")))
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 8) Deduplicate & finalise
        val finalUrls = videoUrls.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        // 9) For each url: prefer calling framework extractor if available; else create ExtractorLink and callback
        var foundAny = false
        for (url in finalUrls) {
            try {
                // Try to let extractor handle it (if your environment has loadExtractor)
                try {
                    // loadExtractor may be provided by CloudStream utilities in some versions.
                    // If present, this will forward host-specific extraction and call our callback.
                    @Suppress("UNCHECKED_CAST")
                    val loadExtractorFn = this::class.java.methods.firstOrNull { it.name == "loadExtractor" }
                    if (loadExtractorFn != null) {
                        // call loadExtractor(url, subtitleCallback, callback)
                        // reflection used to avoid compile error in case method is not present at compile-time
                        loadExtractorFn.invoke(this, url, subtitleCallback, callback)
                        foundAny = true
                        continue
                    }
                } catch (_: Exception) {
                    // fallthrough to manual ExtractorLink if loadExtractor not available
                }

                // Fallback: build an ExtractorLink and call callback
                // NOTE: constructor signature assumed: ExtractorLink(url, name, quality)
                // If your ExtractorLink has a different constructor, replace accordingly.
                val quality = when {
                    url.contains("1080") -> "1080p"
                    url.contains("720") -> "720p"
                    url.contains("480") -> "480p"
                    url.contains(".m3u8") -> "hls"
                    else -> "auto"
                }
                val name = url.substringAfterLast("/").takeIf { it.isNotBlank() } ?: url
                val link = ExtractorLink(url, name, quality)
                callback(link)
                foundAny = true
            } catch (e: Exception) {
                Log.w("GaypornHDfree", "Failed to process extracted url $url : ${e.message}")
            }
        }

        foundAny
    } catch (e: Exception) {
        Log.e("GaypornHDfree", "Error in loadLinks: ${e.message}")
        false
    }
}
} 
