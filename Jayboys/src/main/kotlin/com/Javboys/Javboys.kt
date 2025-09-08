package com.Jayboys

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import org.jsoup.Jsoup
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response

class Jayboys : MainAPI() {
    override var mainUrl = "https://javboys.tv"
    override var name = "Javboys"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "/2025/" to "Latest Updates",  
        "/category/onlyfans/" to "Onlyfans",
        "/category/movies/" to "Movies",
        "/category/asian-gay-porn-hd/" to "Châu Á",
        "/category/tag/gaysianhole/" to "Lỗ Á",
        "/category/western-gay-porn-hd/" to "Châu Mỹ Âu",
        "/category/%e3%82%b2%e3%82%a4%e9%9b%91%e8%aa%8c/" to "Tạp chí",
        "/category/hunk-channel/" to "Hunk Channel",
        "/2025/08/" to "8",
        "/2025/07/" to "7",
        "/2025/06/" to "6",
        "/2025/05/" to "5",
        "/2025/04/" to "4",  
        "/2025/03/" to "3",
        "/2025/02/" to "2",
        "/2025/01/" to "1",
        "/tag/pec_pal48/" to "Pec Pal",
        "/tag/xingfufu/" to "Xing Fu Fu",
        "/tag/xingfufu/" to "Xing Fu Fu",
        "/?s=gangbang" to "Gangbang",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = when {
            request.data.startsWith("/category/") && page > 1 -> "$mainUrl${request.data}page/$page/"
            page > 1 -> "$mainUrl${request.data}page/$page/"
            else -> "$mainUrl${request.data}"
        }

        val ua = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0")
        val document = app.get(url, headers = ua).document
        // Fixed selector - using correct container class
        val home = document.select("div.list-item div.video.col-2").mapNotNull { it.toSearchResult() }

        // Fixed pagination detection
        val hasNext = document.selectFirst("a.next.page-numbers") != null

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = hasNext
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        // Fixed selectors to match actual HTML structure
        val title = this.selectFirst("a.denomination span.title")?.text()?.trim() ?: ""
        val href = this.selectFirst("a.thumb-video")?.attr("href")?.trim() ?: ""
        val posterUrl = this.selectFirst("a.thumb-video img")?.attr("src")?.trim() ?: ""
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("a.denomination span.title")?.text()?.trim() ?: ""
        val href = this.selectFirst("a.thumb-video")?.attr("href")?.trim() ?: ""
        val posterUrl = this.selectFirst("a.thumb-video img")?.attr("src")?.trim() ?: ""
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("div.list-item div.video.col-2").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }
       
        
        override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val recommendations = document.select("div.list-item div.video.col-2").mapNotNull {
            it.toRecommendResult()
    }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
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
    val response = app.get(data)
    val document = response.document
    val pageText = response.text

    var found = false
    val videoUrls = mutableSetOf<String>()

    // --- giữ nguyên cách thu thập cũ ---
    document.select("div#player, div.video-player")
        .mapNotNull { it.attr("data-src").takeIf { u -> u.isNotBlank() && u != "#" } }
        .forEach { videoUrls.add(it) }

    if (videoUrls.isEmpty()) {
        val iframeSrc = document.selectFirst("iframe[src]")?.attr("src")
        iframeSrc?.let { videoUrls.add(it) }
    }

    val button = document.select("div.download-button-wrapper a[href]")?.attr("href")
    button?.let { videoUrls.add(it) }

    // --- bổ sung thêm các nguồn (không xóa gì cũ) ---
    document.select("[data-src]").forEach { el ->
        val v = el.attr("data-src").takeIf { u -> u.isNotBlank() && u != "#" }
        v?.let { videoUrls.add(it) }
    }

    document.select("[src]").forEach { el ->
        val v = el.attr("src").takeIf { u -> u.isNotBlank() }
        v?.let { if (it != "#") videoUrls.add(it) }
    }

    document.select("video source[src], source[src]").forEach {
        val v = it.attr("src").takeIf { u -> u.isNotBlank() }
        v?.let { videoUrls.add(it) }
    }

    val dataVideoRegex = Regex("""data:video\/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/=]+""")
    dataVideoRegex.findAll(pageText).forEach { m -> videoUrls.add(m.value) }

    document.select("video[src^=data:], source[src^=data:], div[src^=data:], iframe[src^=data:]").forEach {
        val v = it.attr("src").takeIf { u -> u.isNotBlank() }
        v?.let { videoUrls.add(it) }
    }

    // reconstruct các data-uri bị tách trong JS (nếu bạn đã cài reconstructDataUris ở Extractors.kt)
    reconstructDataUris(pageText).forEach { videoUrls.add(it) }

    // --- SANITIZE: loại bỏ rỗng, "#", và trim ---
    val sanitized = videoUrls
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "#" }
        .toMutableSet()

    // Debug: in log để kiểm tra
    Log.d("Base64Debug", "Sanitized videoUrls size=${sanitized.size}")
    Log.d("Base64Debug", "Sanitized sample=${sanitized.take(12)}")

    // --- XỬ LÝ: data:video/* trực tiếp, bỏ data: khác, resolve relative, gọi loadExtractor an toàn ---
    for (raw in sanitized) {
        try {
            // Nếu là data-uri video -> trả trực tiếp, không gọi loadExtractor
            if (raw.startsWith("data:video/")) {
                val link = newExtractorLink(
                    source = "Base64",
                    name = "Base64",
                    url = raw,
                    type = INFER_TYPE
                ) {
                    this.referer = data
                    this.quality = Qualities.Unknown.value
                }
                callback(link)
                found = true
                continue
            }

            // Bỏ qua các data: không phải video (vd: data:text/javascript,...)
            if (raw.startsWith("data:")) {
                Log.d("Base64Debug", "Skipping non-video data URI: $raw")
                continue
            }

            // Nếu là relative URL -> resolve với page url
            val resolvedUrl = try {
                if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    raw
                } else {
                    // dùng java.net.URL để resolve relative -> absolute
                    try {
                        val base = java.net.URL(data)
                        java.net.URL(base, raw).toString()
                    } catch (e: Exception) {
                        // fallback: nếu không thể resolve thì giữ raw
                        raw
                    }
                }
            } catch (e: Exception) {
                raw
            }

            // Gọi loadExtractor trong try/catch để tránh NPE khi không tìm extractor
            try {
                val ok = loadExtractor(
                    resolvedUrl,
                    referer = data,
                    subtitleCallback = subtitleCallback
                ) { link ->
                    callback(link)
                }
                if (ok) found = true
            } catch (e: Exception) {
                // Log và tiếp tục, không để crash
                Log.w("Base64Debug", "loadExtractor failed for $resolvedUrl : ${e.message}")
            }
        } catch (e: Exception) {
            Log.w("Base64Debug", "Error processing url '$raw' : ${e.message}")
        }
    }

    return found
    }
}
