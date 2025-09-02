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

    override suspend fun load(url: String): LoadResponse? {
    val doc = app.get(url).document
    val title = doc.selectFirst("h1")?.text() ?: "No title"
    val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
    val description = doc.selectFirst("meta[name=description]")?.attr("content")

    return MovieLoadResponse(
        name = title,
        url = url,
        apiName = this.name,
        type = TvType.Movie,
        dataUrl = url,
        posterUrl = poster,
        year = null,
        plot = description
    )
}


    // --------- helper: try call runtime loadExtractor (reflection) or fallback to callback ----------
    private fun callLoadExtractorOrCallback(
        fixedUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Try reflection: find any method named loadExtractor and attempt to invoke it.
        try {
            val methods = this::class.java.methods.filter { it.name == "loadExtractor" }
            for (m in methods) {
                try {
                    // common signatures: (String, (SubtitleFile)->Unit, (ExtractorLink)->Unit) or (String, (ExtractorLink)->Unit)
                    if (m.parameterCount == 3) {
                        m.invoke(this, fixedUrl, subtitleCallback, callback)
                        return true
                    } else if (m.parameterCount == 2) {
                        m.invoke(this, fixedUrl, callback)
                        return true
                    }
                    // ignore other signatures
                } catch (t: Throwable) {
                    // try next method
                    Log.w("GaypornHDfree", "reflection loadExtractor invoke failed: ${t.message}")
                }
            }
        } catch (e: Exception) {
            Log.w("GaypornHDfree", "Reflection search for loadExtractor failed: ${e.message}")
        }

        // Fallback: create ExtractorLink and call callback so player still has links
        return try {
            val quality = when {
                fixedUrl.contains("1080") -> "1080p"
                fixedUrl.contains("720") -> "720p"
                fixedUrl.contains("480") -> "480p"
                fixedUrl.contains(".m3u8") -> "hls"
                else -> "auto"
            }
            val name = fixedUrl.substringAfterLast("/").takeIf { it.isNotBlank() } ?: fixedUrl
            callback(ExtractorLink(fixedUrl, name, quality))
            true
        } catch (e: Exception) {
            Log.w("GaypornHDfree", "Fallback callback failed: ${e.message}")
            false
        }
    }

    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    return try {
        // 1) Lấy HTML an toàn (OkHttp fallback)
        val html = fetchHtmlSafely(data, mapOf("Referer" to mainUrl)).ifEmpty {
            try {
                Jsoup.connect(data)
                    .headers(standardHeaders)
                    .userAgent(standardHeaders["User-Agent"] ?: "")
                    .timeout(15000)
                    .get()
                    .html()
            } catch (e: Exception) { "" }
        }

        if (html.isBlank()) {
            Log.w("GaypornHDfree", "loadLinks: empty html for $data")
            return false
        }

        val doc = Jsoup.parse(html, data)
        val videoUrls = mutableSetOf<String>()

        // 2) Thu thập các nguồn phổ biến
        listOf(
            "video source[src]", "video[src]", "iframe[src]", "[data-src]", "embed[src]",
            "a[href$=.mp4]", "a[href$=.m3u8]", "a[href*='.mp4']", "a[href*='.m3u8']"
        ).forEach { sel ->
            doc.select(sel).forEach { el ->
                val found = listOf("src", "data-src", "href").map { k -> el.attr(k) }.firstOrNull { it.isNotBlank() }
                found?.let { videoUrls.add(it) }
            }
        }

        // meta og:video
        doc.select("meta[property=og:video], meta[property=og:video:secure_url], meta[name=og:video]").forEach {
            val c = it.attr("content")
            if (c.isNotBlank()) videoUrls.add(c)
        }

        // tìm trong script các pattern file/url
        val scriptsCombined = doc.select("script").map { it.data() + it.html() }.joinToString("\n")
        val fileRegex = Regex("""(?i)(?:file|src|url)\s*[:=]\s*['"]((?:https?:)?\/\/[^'"\s]+)['"]""")
        fileRegex.findAll(scriptsCombined).forEach { m ->
            val u = m.groups[1]?.value ?: ""
            if (u.isNotBlank()) videoUrls.add(u.replace("\\/", "/"))
        }

        // fallback: direct links by extensions
        val genericRegex = Regex("""https?:\/\/[^\s'"]+?\.(mp4|m3u8|webm|mpd)(\?[^\s'"]*)?""", RegexOption.IGNORE_CASE)
        genericRegex.findAll(html).forEach { m -> videoUrls.add(m.value.replace("\\/", "/")) }

        // 3) Nếu có iframe/embed pointing to other page thì try fetch để lấy thêm links
        val iframeCandidates = videoUrls.filter { it.contains("/e/") || it.contains("embed") || it.contains("player") }.toList()
        for (ifr in iframeCandidates) {
            try {
                val ifHtml = fetchHtmlSafely(ifr, mapOf("Referer" to data)).ifEmpty {
                    try {
                        Jsoup.connect(ifr).headers(standardHeaders).userAgent(standardHeaders["User-Agent"] ?: "").timeout(8000).get().html()
                    } catch (_: Exception) { "" }
                }
                if (ifHtml.isNotBlank()) {
                    genericRegex.findAll(ifHtml).forEach { m -> videoUrls.add(m.value.replace("\\/", "/")) }
                    fileRegex.findAll(ifHtml).forEach { m ->
                        val u = m.groups[1]?.value ?: ""
                        if (u.isNotBlank()) videoUrls.add(u.replace("\\/", "/"))
                    }
                }
            } catch (_: Exception) { /* ignore iframe failures */ }
        }

        // 4) Chuẩn hoá và gọi loadExtractor trực tiếp cho từng URL
        var called = false
        videoUrls.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { raw ->
            try {
                val fixedUrl = when {
                    raw.startsWith("http://") || raw.startsWith("https://") -> raw
                    raw.startsWith("//") -> "https:$raw"
                    raw.startsWith("/") -> "$mainUrl$raw"
                    else -> if (raw.startsWith("http")) raw else "$mainUrl/${raw.removePrefix("/")}"
                }.replace("\\/", "/")

                Log.d("GaypornHDfree", "Calling loadExtractor for: $fixedUrl")

                // Gọi loadExtractor trực tiếp (hai signature phổ biến được thử)
                try {
                    // Thử signature phổ biến: loadExtractor(url, subtitleCallback, callback)
                    this::class.java.getMethod("loadExtractor", String::class.java, kotlin.jvm.functions.Function1::class.java, kotlin.jvm.functions.Function1::class.java)
                        .invoke(this, fixedUrl, subtitleCallback, callback)
                    called = true
                } catch (noSuch: NoSuchMethodException) {
                    try {
                        // Thử signature: loadExtractor(url, callback)
                        this::class.java.getMethod("loadExtractor", String::class.java, kotlin.jvm.functions.Function1::class.java)
                            .invoke(this, fixedUrl, callback)
                        called = true
                    } catch (noSuch2: NoSuchMethodException) {
                        // Nếu Extractor.kt thực sự định nghĩa loadExtractor nhưng với signature khác,
                        // bạn cần thay 2 dòng trên cho khớp signature — hoặc giữ code này và đảm bảo Extractor.kt có 1 trong 2 signature.
                        Log.w("GaypornHDfree", "loadExtractor method not found with expected signatures for $fixedUrl")
                    }
                }

            } catch (e: Exception) {
                Log.w("GaypornHDfree", "Error calling loadExtractor for $raw : ${e.message}")
            }
        }

        called
    } catch (e: Exception) {
        Log.e("GaypornHDfree", "Error in loadLinks: ${e.message}")
        false
    }
}

} 
