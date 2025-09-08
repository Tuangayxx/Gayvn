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
    // Lấy response 1 lần (tránh gọi app.get nhiều lần)
    val response = app.get(data)
    val document = response.document
    val pageText = response.text
    var found = false

    // Tập hợp các URL thô thu thập được
    val videoUrls = mutableSetOf<String>()

    // --- các cách thu thập hiện có (giữ nguyên) ---
    // data-src từ player
    document.select("div#player, div.video-player").forEach {
        it.attr("data-src").takeIf { u -> !u.isNullOrBlank() && u != "#" }?.let { videoUrls.add(it) }
    }

    // fallback iframe
    val iframeSrc = document.selectFirst("iframe[src]")?.attr("src")
    iframeSrc?.let { videoUrls.add(it) }

    // download button (giữ nguyên)
    document.selectFirst("div.download-button-wrapper a[href]")?.attr("href")?.let { videoUrls.add(it) }

    // --- PHẦN BỔ SUNG: nhiều cách khác để thu thập URL ---
    // 1) tất cả attribute data-src (nhiều site chèn link ở chỗ khác)
    document.select("[data-src]").forEach { el ->
        el.attr("data-src").takeIf { u -> !u.isNullOrBlank() && u != "#" }?.let { videoUrls.add(it) }
    }

    // 2) tất cả attribute src (video, source, script, img, div có src)
    document.select("[src]").forEach { el ->
        el.attr("src").takeIf { u -> !u.isNullOrBlank() }?.let { if (it != "#") videoUrls.add(it) }
    }

    // 3) thẻ <source> trong <video>
    document.select("video source[src], source[src]").forEach {
        it.attr("src").takeIf { u -> !u.isNullOrBlank() }?.let { videoUrls.add(it) }
    }

    // 4) tìm data-uri base64 trong toàn bộ HTML/JS (những video được chèn trong script)
    val dataVideoRegex = Regex("""data:video\/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/=]+""")
    dataVideoRegex.findAll(pageText).forEach { match ->
        videoUrls.add(match.value)
    }

    // 5) nếu element có attr src bắt đầu bằng data:, thêm nhưng chỉ khi là data:video/...
    document.select("video[src^=data:], source[src^=data:], div[src^=data:], iframe[src^=data:]").forEach {
        it.attr("src").takeIf { u -> !u.isNullOrBlank() }?.let { videoUrls.add(it) }
    }

    // Debug (tuỳ bạn bật/tắt)
    // Log.d("Base64Debug", "Collected raw videoUrls: $videoUrls")

    // --- LỌC: loại bỏ mọi data: URI không phải video (vì gọi app.get trên data:* sẽ lỗi) ---
    val filteredUrls = videoUrls
        .map { it.trim() }
        .filter { !(it.startsWith("data:", ignoreCase = true) && !it.startsWith("data:video/", ignoreCase = true)) }
        .toList()

    // Gọi loadExtractor cho mỗi URL (giữ nguyên cơ chế cũ)
    filteredUrls.amap { url ->
        try {
            val ok = loadExtractor(
                url,
                referer = data,
                subtitleCallback = subtitleCallback
            ) { link ->
                callback(link)
            }
            if (ok) found = true
        } catch (e: Exception) {
            // Bắt lỗi để tránh crash khi extractor nào đó ném ngoại lệ
            // Log.e("Javboys", "Error loading extractor for $url : ${e.message}")
        }
    }

    return found
}
}
