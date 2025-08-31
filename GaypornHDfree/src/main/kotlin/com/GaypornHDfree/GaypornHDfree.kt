package com.GaypornHDfree

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import org.jsoup.Jsoup
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.network.*
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response
import com.lagradost.cloudstream3.network.WebViewResolver
import java.net.URLEncoder

class GaypornHDfree : MainAPI() {
    override var mainUrl = "https://gaypornhdfree.com"
    override var name = "GaypornHDfree"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "/" to "Latest",
        "$mainUrl/category/onlyfans/" to "Onlyfans",
    )

    // Thêm User-Agent và headers chuẩn
    private val standardHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Accept-Encoding" to "gzip, deflate",
        "Connection" to "keep-alive",
        "Upgrade-Insecure-Requests" to "1"
    )

    // ---------- SỬA: getMainPage ----------
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

        // Nếu HTML nghi ngờ (title rỗng / challenge), log snippet và thử fallback render
        if (document.title().isNullOrBlank() || document.html().length < 300 || document.html().contains("challenge-platform") ||
            document.selectFirst("div.main-wrapper")?.text()?.contains("gaypornhdfree.com cần đánh giá") == true) {

            Log.w("GaypornHDfree", "Suspect HTML detected for $url. Snippet: ${document.html().take(800)}")

            // 1) Thử WebViewResolver (render JS => lấy HTML đã render) -- nếu lib có API khác, đổi tương ứng
            try {
                Log.d("GaypornHDfree", "Trying WebViewResolver fallback for $url")
                val renderedHtml = WebViewResolver.getHtml(url, headers = cfHeaders) // đổi tên hàm nếu cần
                val renderedDoc = Jsoup.parse(renderedHtml, url)
                if (!renderedDoc.title().isNullOrBlank() && !renderedDoc.html().contains("challenge-platform")) {
                    return parseMainPageContent(renderedDoc, request.name)
                } else {
                    Log.w("GaypornHDfree", "WebViewResolver returned challenge/empty for $url")
                }
            } catch (e: Exception) {
                Log.w("GaypornHDfree", "WebViewResolver failed: ${e.message}")
            }

            // 2) Thử CloudflareKiller (nếu có) để solve challenge
            try {
                Log.d("GaypornHDfree", "Trying CloudflareKiller fallback for $url")
                val solvedHtml = CloudflareKiller.solve(url, headers = cfHeaders) // đổi tên nếu API khác
                val solvedDoc = Jsoup.parse(solvedHtml, url)
                if (!solvedDoc.title().isNullOrBlank() && !solvedDoc.html().contains("challenge-platform")) {
                    return parseMainPageContent(solvedDoc, request.name)
                } else {
                    Log.w("GaypornHDfree", "CloudflareKiller returned challenge/empty for $url")
                }
            } catch (e: Exception) {
                Log.w("GaypornHDfree", "CloudflareKiller failed: ${e.message}")
            }

            // 3) Cuối cùng: retry nhẹ bằng HTTP client (fallback)
            try {
                kotlinx.coroutines.delay(2000)
                val retryDocument = app.get(url, headers = cfHeaders).document
                if (retryDocument.title().isNullOrBlank() || retryDocument.html().contains("challenge-platform")) {
                    Log.w("GaypornHDfree", "Still Cloudflare after fallback - returning empty for $url")
                    return newHomePageResponse(HomePageList(request.name, listOf()), false)
                } else {
                    return parseMainPageContent(retryDocument, request.name)
                }
            } catch (e: Exception) {
                Log.e("GaypornHDfree", "Final retry failed in getMainPage: ${e.message}")
                return newHomePageResponse(HomePageList(request.name, listOf()), false)
            }
        } else {
            return parseMainPageContent(document, request.name)
        }
    } catch (e: Exception) {
        Log.e("GaypornHDfree", "Error in getMainPage: ${e.message}")
        return newHomePageResponse(HomePageList(request.name, listOf()), false)
    }
}

// ---------- SỬA: parseMainPageContent ----------
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

// ---------- SỬA: Element.toSearchResult() ----------
private fun Element.toSearchResult(): SearchResponse? {
    return try {
        Log.d("GaypornHDfree", "Parsing element HTML: ${this.html()}")

        // Thử tất cả các selector có thể để tìm title và link
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

        // Tìm poster - thử tất cả các khả năng
        val img = this.selectFirst("img")
            ?: this.selectFirst("video")
            ?: titleElement.selectFirst("img")

        val poster = img?.let { imgEl ->
            val attrs = listOf("data-src", "data-lazy-src", "data-original", "src", "poster")
            attrs.map { attr -> imgEl.attr(attr) }
                .firstOrNull { it.isNotEmpty() && !it.contains("placeholder") }
        } ?: ""

        Log.d("GaypornHDfree", "Found: title='$title', href='$href', poster='$poster'")

        // Chỉ gán poster nếu có giá trị hợp lệ
        val searchResp = newMovieSearchResponse(title, href, TvType.NSFW) {
            if (poster.isNotBlank()) {
                this.posterUrl = when {
                    poster.startsWith("http") -> poster
                    poster.startsWith("//") -> "https:$poster"
                    poster.startsWith("/") -> "$mainUrl$poster"
                    else -> "$mainUrl/$poster"
                }
            }
        }
        searchResp
    } catch (e: Exception) {
        Log.e("GaypornHDfree", "Error in toSearchResult: ${e.message}")
        null
    }
}

// ---------- SỬA: search(...) - sửa lỗi for loop ----------
override suspend fun search(query: String): List<SearchResponse> {
    val searchResponse = mutableListOf<SearchResponse>()
    val seenUrls = mutableSetOf<String>()

    try {
        // sửa from "for (i in 1.3)" -> "for (i in 1..3)"
        for (i in 1..3) {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$mainUrl/?s=$encodedQuery&page=$i"

            val document = app.get(searchUrl, headers = standardHeaders).document

            // Log để debug
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

            // Thêm delay nhỏ để tránh spam requests
            kotlinx.coroutines.delay(500)
        }
    } catch (e: Exception) {
        Log.e("GaypornHDfree", "Error in search: ${e.message}")
    }

    return searchResponse
}

// ---------- SỬA: load(...) và parseLoadResponse(...) ----------
override suspend fun load(url: String): LoadResponse {
    return try {
        val cfHeaders = standardHeaders + mapOf(
            "Cookie" to "cf_clearance=1; __cfduid=1; hasVisited=1",
            "Cache-Control" to "no-cache"
        )

        var document = app.get(url, headers = cfHeaders).document

        if (document.title().isNullOrBlank() || document.html().contains("challenge-platform") || document.html().length < 300) {
            Log.w("GaypornHDfree", "Suspect HTML on load for $url. Snippet: ${document.html().take(800)}")

            // Thử WebViewResolver
            try {
                val renderedHtml = WebViewResolver.getHtml(url, headers = cfHeaders)
                val renderedDoc = Jsoup.parse(renderedHtml, url)
                if (!renderedDoc.title().isNullOrBlank() && !renderedDoc.html().contains("challenge-platform")) {
                    return parseLoadResponse(renderedDoc, url)
                } else {
                    Log.w("GaypornHDfree", "WebViewResolver returned challenge/empty on load.")
                }
            } catch (e: Exception) {
                Log.w("GaypornHDfree", "WebViewResolver on load failed: ${e.message}")
            }

            // Thử CloudflareKiller
            try {
                val solvedHtml = CloudflareKiller.solve(url, headers = cfHeaders)
                val solvedDoc = Jsoup.parse(solvedHtml, url)
                if (!solvedDoc.title().isNullOrBlank() && !solvedDoc.html().contains("challenge-platform")) {
                    return parseLoadResponse(solvedDoc, url)
                } else {
                    Log.w("GaypornHDfree", "CloudflareKiller returned challenge/empty on load.")
                }
            } catch (e: Exception) {
                Log.w("GaypornHDfree", "CloudflareKiller on load failed: ${e.message}")
            }

            // fallback retry
            kotlinx.coroutines.delay(2000)
            val retryDoc = app.get(url, headers = cfHeaders).document
            if (retryDoc.html().contains("challenge-platform") || retryDoc.title().isNullOrBlank()) {
                throw Exception("Cloudflare protection active")
            }
            return parseLoadResponse(retryDoc, url)
        } else {
            return parseLoadResponse(document, url)
        }
    } catch (e: Exception) {
        Log.e("GaypornHDfree", "Error in load: ${e.message}")
        throw e
    }
}

private suspend fun parseLoadResponse(document: Document, url: String): LoadResponse {
    // Thử nhiều cách để lấy title
    val title = listOf(
        document.selectFirst("meta[property='og:title']")?.attr("content"),
        document.selectFirst("title")?.text()?.replace(" - GayPornHDfree", ""),
        document.selectFirst("h1")?.text(),
        document.selectFirst(".video-title, .entry-title")?.text()
    ).firstOrNull { !it.isNullOrBlank() }?.trim() ?: "Unknown Title"

    // Thử nhiều cách để lấy poster
    val poster = listOf(
        document.selectFirst("meta[property='og:image']")?.attr("content"),
        document.selectFirst("video")?.attr("poster"),
        document.selectFirst(".video-thumb img, .wp-post-image")?.attr("src")
    ).firstOrNull { !it.isNullOrBlank() } ?: ""

    // Thử nhiều cách để lấy description
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