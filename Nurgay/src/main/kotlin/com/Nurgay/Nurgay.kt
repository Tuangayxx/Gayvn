package com.Nurgay

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.nodes.Element
import java.io.IOException
import com.lagradost.api.Log


class Nurgay : MainAPI() {
    override var mainUrl = "https://nurgay.to"
    override var name = "Nurgay"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        "/?filter=latest"                         to "Latest",
        "/?filter=most-viewed"                    to "Most Viewed",
        "/asiaten"                                to "Asian",
        "/gruppensex"                             to "Group Sex",
        "/bisex"                                  to "Bisexual",
        "/hunks"                                  to "Hunks",
        "/latino"                                 to "Latino",
        "/muskeln"                                to "Muscle",
        "/bareback"                               to "Bareback",
    )    

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val pageUrl = if (page == 1) 
        "$mainUrl${request.data}" 
    else 
        "$mainUrl/page/$page${request.data}" // ✅ Sửa pagination

    val document = app.get(pageUrl).document
    val home = document.select("article.loop-video").mapNotNull { it.toSearchResult() }

    return newHomePageResponse(
        list = HomePageList(
            name = request.name,
            list = home,
            isHorizontalImages = false
        ),
        hasNext = true
    )
}

private fun Element.toSearchResult(): SearchResponse {
    val title = this.select("header.entry-header span").text() // ✅ Sửa lấy text
    val href = fixUrl(this.select("a").attr("href"))
    val posterUrl = fixUrlNull(this.select("img").attr("data-src"))
    
    return newMovieSearchResponse(title, href, TvType.NSFW) {
        this.posterUrl = posterUrl
    }
}

override suspend fun search(query: String): List<SearchResponse> {
    val searchResponse = mutableListOf<SearchResponse>()

    for (i in 1..7) {
        // ✅ Sửa URL search: thêm `&page=i`
        val document = app.get("$mainUrl/?s=$query&page=$i").document
        val results = document.select("article.loop-video").mapNotNull { it.toSearchResult() }

        if (results.isEmpty()) break
        searchResponse.addAll(results)
    }

    return searchResponse
}
   

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: ""
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
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
                Jsoup.connect(data)
                    .headers(standardHeaders)
                    .userAgent(standardHeaders["User-Agent"] ?: "")
                    .timeout(15_000)
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

        // 2) Các selector hay chứa nguồn
        val selectors = listOf(
            "video source[src]",
            "video[src]",
            "iframe[src]",
            "iframe[data-src]",
            "embed[src]",
            "[data-src]",
            "a[href$=.mp4]",
            "a[href$=.m3u8]",
            "a[href*='.mp4']",
            "a[href*='.m3u8']"
        )

        selectors.forEach { sel ->
            doc.select(sel).forEach { el ->
                val found = listOf("src", "data-src", "href").map { k -> el.attr(k) }.firstOrNull { it.isNotBlank() }
                found?.let { videoUrls.add(it) }
            }
        }

        // 3) Tìm trong script JSON / player config / og:video
        doc.select("meta[property=og:video], meta[property=og:video:secure_url], meta[name=og:video]").forEach {
            val c = it.attr("content")
            if (c.isNotBlank()) videoUrls.add(c)
        }

        val scriptsCombined = doc.select("script").map { it.data() + it.html() }.joinToString("\n")
        val fileRegex = Regex("""(?i)(?:file|src|url)\s*[:=]\s*['"]((?:https?:)?\/\/[^'"\s]+)['"]""")
        fileRegex.findAll(scriptsCombined).forEach { m ->
            val u = m.groups[1]?.value ?: ""
            if (u.isNotBlank()) videoUrls.add(u.replace("\\/", "/"))
        }

        // 4) Heuristic: direct links to common video ext
        val genericRegex = Regex("""https?:\/\/[^\s'"]+?\.(mp4|m3u8|webm|mpd)(\?[^\s'"]*)?""", RegexOption.IGNORE_CASE)
        genericRegex.findAll(html).forEach { m -> videoUrls.add(m.value.replace("\\/", "/")) }

        // 5) Nếu có iframe/embed pointing to another page, try fetch minimal iframe content and extract more links
        val iframeCandidates = videoUrls.filter { it.contains("/e/") || it.contains("embed") || it.contains("player") }
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

        Log.d("GaypornHDfree", "Found ${videoUrls.size} candidate video URLs for $data")

        // 6) Chuẩn hoá URL & gọi loadExtractor cho từng URL
        var called = false
        videoUrls.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { raw ->
            try {
                val fixedUrl = when {
                    raw.startsWith("http://") || raw.startsWith("https://") -> raw
                    raw.startsWith("//") -> "https:$raw"
                    raw.startsWith("/") -> "$mainUrl${raw}"
                    else -> if (raw.startsWith("http")) raw else "$mainUrl/${raw.removePrefix("/")}"
                }.replace("\\/", "/")

                Log.d("GaypornHDfree", "Calling loadExtractor for: $fixedUrl")
                // Gọi trực tiếp loadExtractor để extractor đã đăng ký xử lý (không tạo ExtractorLink thủ công)
                loadExtractor(fixedUrl, subtitleCallback, callback)
                called = true
            } catch (e: Throwable) {
                Log.w("GaypornHDfree", "loadExtractor failed for $raw : ${e.message}")
            }
        }

        called
    } catch (e: Exception) {
        Log.e("GaypornHDfree", "Error in loadLinks: ${e.message}")
        false
    }
}
}