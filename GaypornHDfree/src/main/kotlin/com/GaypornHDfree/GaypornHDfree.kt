package com.GaypornHDfree

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.api.*
import com.lagradost.cloudstream3.network.*
import kotlinx.coroutines.delay
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.net.URLEncoder

class GaypornHDfree : MainAPI() {
    override val name: String = "GaypornHDfree"
    override val mainUrl = "https://gaypornhdfree.com"
    override val lang = "vi"
    override val supportedTypes = setOf(TvType.NSFW)
    override val version = "1.0"

    // OkHttp client for safe fetches (gzip handling fallback)
    private val fallbackHttpClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    // Safe HTML fetcher: avoids 'br' responses and attempts to decode gzip if needed.
    private fun fetchHtmlSafely(url: String, extraHeaders: Map<String, String> = mapOf()): String {
        return try {
            val headersMap = mutableMapOf<String, String>()
            headersMap.putAll(extraHeaders)
            // avoid brotli to reduce binary responses
            headersMap["Accept-Encoding"] = "gzip, deflate"
            headersMap["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            headersMap["User-Agent"] = headersMap["User-Agent"]
                ?: "Mozilla/5.0 (Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116 Mobile Safari/537.36"
            val headers = Headers.of(headersMap)

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
                    try {
                        return GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } catch (_: Exception) {
                        // fallback to raw UTF-8 decode
                        return try { String(bytes, Charsets.UTF_8) } catch (_: Exception) { "" }
                    }
                } else {
                    val text = try { String(bytes, Charsets.UTF_8) } catch (_: Exception) { "" }
                    val nonPrintable = text.count { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
                    if (text.isBlank() || (text.length > 0 && nonPrintable.toDouble() / text.length > 0.30)) {
                        "" // suspicious binary
                    } else text
                }
            }
        } catch (e: Exception) {
            Log.w("GaypornHDfree", "fetchHtmlSafely failed for $url: ${e.message}")
            ""
        }
    }

    // Resolve relative URLs safely against mainUrl
    private fun fixUrl(href: String?): String {
        if (href == null) return ""
        var u = href.trim()
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
            // Thêm cookies và headers để giảm khả năng bị chặn
            val cfHeaders = standardHeaders + mapOf(
                "Cookie" to "cf_clearance=1; __cfduid=1; hasVisited=1",
                "Cache-Control" to "no-cache",
                "Pragma" to "no-cache"
            )

            var document = app.get(url, headers = cfHeaders).document

            val suspect = document.title().isNullOrBlank() ||
                    document.html().length < 300 ||
                    document.html().contains("challenge-platform") ||
                    document.selectFirst("div.main-wrapper")?.text()?.contains("đánh giá") == true ||
                    document.html().contains("Ray ID")

            if (suspect) {
                Log.w("GaypornHDfree", "Suspect HTML detected for $url. Snippet: ${document.html().take(300)}")

                // 1) Try safe fetch with OkHttp (gzip handling)
                val safeHtml = fetchHtmlSafely(url, mapOf(
                    "Referer" to mainUrl,
                    "Cookie" to "hasVisited=1",
                    "User-Agent" to "Mozilla/5.0 (Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116 Mobile Safari/537.36"
                ))

                if (safeHtml.isNotBlank() && !safeHtml.contains("challenge-platform") && !safeHtml.contains("Ray ID")) {
                    val parsed = Jsoup.parse(safeHtml, url)
                    return parseMainPageContent(parsed, request.name)
                } else {
                    Log.w("GaypornHDfree", "fetchHtmlSafely did not return usable HTML for $url")
                }

                // 2) Try alternate UA
                val altHtml = fetchHtmlSafely(url, mapOf(
                    "Referer" to mainUrl,
                    "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.188 Safari/537.36"
                ))
                if (altHtml.isNotBlank() && !altHtml.contains("challenge-platform") && !altHtml.contains("Ray ID")) {
                    val parsedAlt = Jsoup.parse(altHtml, url)
                    return parseMainPageContent(parsedAlt, request.name)
                }

                Log.e("GaypornHDfree", "Cloudflare/unrenderable page for $url - returning empty main page (after safe fetch)")
                return newHomePageResponse(HomePageList(request.name, listOf()), false)
            } else {
                return parseMainPageContent(document, request.name)
            }
        } catch (e: Exception) {
            Log.e("GaypornHDfree", "Error in getMainPage: ${e.message}")
            return newHomePageResponse(HomePageList(request.name, listOf()), false)
        }
    }

    // ----------------- Parse main page content -----------------
    private fun parseMainPageContent(document: Document, requestName: String): HomePageResponse {
        // Debug: Log title + short snippet để dễ debug Cloudflare / empty HTML
        Log.d("GaypornHDfree", "Page title: ${document.title()}")
        Log.d("GaypornHDfree", "Page classes: ${document.select("div").take(5).map { it.className() }}")
        if (document.title().isNullOrBlank() || document.html().length < 300) {
            Log.w("GaypornHDfree", "Short HTML/snippet: ${document.html().take(800)}")
        }

        val home = document.select("div.videopost, .video-item, .post-item, .thumb-item, div[class*='video'], article, .post")
            .mapNotNull { element ->
                try {
                    Log.d("GaypornHDfree", "Processing element with classes: ${element.className()}")
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

    // ----------------- Search helper -----------------
    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        val seenUrls = mutableSetOf<String>()

        try {
            for (i in 1..3) {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "$mainUrl/?s=$encodedQuery&page=$i"

                val document = app.get(searchUrl, headers = standardHeaders).document

                Log.d("GaypornHDfree", "Search page classes: ${document.select("div").take(5).map { it.className() }}")

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

    // ----------------- Load page (video page) -----------------
    override suspend fun load(url: String): LoadResponse {
        return try {
            val cfHeaders = standardHeaders + mapOf(
                "Cookie" to "cf_clearance=1; __cfduid=1; hasVisited=1",
                "Cache-Control" to "no-cache"
            )

            var document = app.get(url, headers = cfHeaders).document

            if (document.title().isNullOrBlank() || document.html().contains("challenge-platform") || document.html().length < 300) {
                Log.w("GaypornHDfree", "Suspect HTML on load for $url. Snippet: ${document.html().take(800)}")

                // 1) Retry with short delay
                try {
                    delay(2000)
                    val retryDoc = app.get(url, headers = cfHeaders).document
                    if (!retryDoc.title().isNullOrBlank() && !retryDoc.html().contains("challenge-platform")) {
                        return parseLoadResponse(retryDoc, url)
                    }
                    Log.w("GaypornHDfree", "Retry returned challenge/empty on load for $url")
                } catch (e: Exception) {
                    Log.w("GaypornHDfree", "Retry failed on load: ${e.message}")
                }

                // 2) Safe fetch via OkHttp
                val safeHtml = fetchHtmlSafely(url, mapOf(
                    "Referer" to mainUrl,
                    "User-Agent" to "Mozilla/5.0 (Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116 Mobile Safari/537.36"
                ))
                if (safeHtml.isNotBlank() && !safeHtml.contains("challenge-platform") && !safeHtml.contains("Ray ID")) {
                    val doc = Jsoup.parse(safeHtml, url)
                    return parseLoadResponse(doc, url)
                }

                // 3) Try alternate UA
                val altHtml = fetchHtmlSafely(url, mapOf(
                    "Referer" to mainUrl,
                    "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.188 Safari/537.36"
                ))
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

    // ----------------- Parse load response -----------------
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

    // ----------------- Element -> SearchResult -----------------
    private fun Element.toSearchResult(): SearchResponse? {
        return try {
            Log.d("GaypornHDfree", "Parsing element HTML: ${this.html()}")

            val titleElement = this.selectFirst("a[href*='/video/']")
                ?: this.selectFirst("a[href*='.html']")
                ?: this.selectFirst("a[title]")
                ?: this.selectFirst("h2 a, h3 a, h4 a")
                ?: this.selectFirst("div.title a")
                ?: this.selectFirst("div.video-title a")
                ?: this.selectFirst("div.deno.video-title a")
                ?: this.selectFirst("a")

            if (titleElement == null) {
                Log.e("GaypornHDfree", "No title element found")
                return null
            }

            val title = titleElement.text().trim().ifEmpty {
                titleElement.attr("title").trim()
            }.ifEmpty {
                titleElement.attr("alt").trim()
            }

            if (title.isEmpty()) {
                Log.e("GaypornHDfree", "No title text found")
                return null
            }

            val href = fixUrl(titleElement.attr("href"))
            if (href.isEmpty() || href == mainUrl) {
                Log.e("GaypornHDfree", "Invalid href: $href")
                return null
            }

            val img = this.selectFirst("img")
                ?: this.selectFirst("video")
                ?: titleElement.selectFirst("img")

            val poster = img?.let { imgEl ->
                val attrs = listOf("data-src", "data-lazy-src", "data-original", "src", "poster")
                attrs.map { attr -> imgEl.attr(attr) }
                    .firstOrNull { it.isNotEmpty() && !it.contains("placeholder") }
            } ?: ""

            Log.d("GaypornHDfree", "Found: title='$title', href='$href', poster='$poster'")

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
}



    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val headers = standardHeaders + mapOf("Referer" to data)
            val document = app.get(data, headers = headers).document
            val videoUrls = mutableSetOf<String>()

            // Tìm tất cả các nguồn video có thể
            val selectors = listOf(
                "iframe[src]",
                "iframe[data-src]", 
                "video source[src]",
                "video[src]",
                "div.video-player[data-src]",
                "div.player-container iframe",
                "embed[src]",
                ".download-button-wrapper a[href]",
                "a[href*='.mp4']",
                "a[href*='.m3u8']"
            )

            selectors.forEach { selector ->
                document.select(selector).forEach { element ->
                    val url = listOf("src", "data-src", "href").map { 
                        element.attr(it) 
                    }.firstOrNull { it.isNotBlank() }
                    
                    url?.let { videoUrls.add(it) }
                }
            }

            // Tìm trong JavaScript
            val scriptTags = document.select("script").map { it.data() }.joinToString(" ")
            val urlPatterns = listOf(
                Regex("(?:src|url)\\s*[:=]\\s*[\"'](https?://[^\"']+)[\"']"),
                Regex("player_url\\s*[:=]\\s*[\"'](https?://[^\"']+)[\"']"),
                Regex("video_url\\s*[:=]\\s*[\"'](https?://[^\"']+)[\"']")
            )
            
            urlPatterns.forEach { pattern ->
                pattern.findAll(scriptTags).forEach { match ->
                    videoUrls.add(match.groupValues[1])
                }
            }

            Log.d("GaypornHDfree", "Found ${videoUrls.size} video URLs")

            // Process tất cả URLs
            videoUrls.forEach { url ->
                try {
                    val fixedUrl = when {
                        url.startsWith("http") -> url
                        url.startsWith("//") -> "https:$url"
                        else -> "$mainUrl/${url.removePrefix("/")}"
                    }
                    
                    Log.d("GaypornHDfree", "Processing URL: $fixedUrl")
                    
                    // Thử extract với loadExtractor
                    Log.d("GaypornHDfree", "Processing URL: $fixedUrl")
                    loadExtractor(fixedUrl, subtitleCallback, callback)
                } catch (e: Exception) {
                    Log.e("GaypornHDfree", "Error processing URL $url: ${e.message}")
                }
            }

            videoUrls.isNotEmpty()
        } catch (e: Exception) {
            Log.e("GaypornHDfree", "Error in loadLinks: ${e.message}")
            false
        }
    }
}