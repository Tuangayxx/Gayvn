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

    // --- bổ sung thêm các nguồn ---
    // 1) tất cả data-src
    document.select("[data-src]").forEach { el ->
        val v = el.attr("data-src").takeIf { u -> u.isNotBlank() && u != "#" }
        v?.let { videoUrls.add(it) }
    }

    // 2) tất cả src attrs (video, source, div, img, iframe...)
    document.select("[src]").forEach { el ->
        val v = el.attr("src").takeIf { u -> u.isNotBlank() }
        v?.let { if (it != "#") videoUrls.add(it) }
    }

    // 3) <video><source>
    document.select("video source[src], source[src]").forEach {
        val v = it.attr("src").takeIf { u -> u.isNotBlank() }
        v?.let { videoUrls.add(it) }
    }

    // 4) regex tìm data-uri liền mạch trong toàn bộ HTML/JS
    val dataVideoRegex = Regex("""data:video\/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/=]+""")
    dataVideoRegex.findAll(pageText).forEach { m -> videoUrls.add(m.value) }

    // 5) nếu có element có src bắt đầu bằng data:
    document.select("video[src^=data:], source[src^=data:], div[src^=data:], iframe[src^=data:]").forEach {
        val v = it.attr("src").takeIf { u -> u.isNotBlank() }
        v?.let { videoUrls.add(it) }
    }

    // 6) **FALLBACK mạnh**: nếu base64 bị tách trong JS (ví dụ '...base64,AAA' + 'BBB...'),
    //    thì tìm vị trí "data:video/" rồi thu thập từng ký tự base64, bỏ qua dấu nháy và dấu cộng.
    fun tryReconstructDataUris(text: String): List<String> {
        val out = mutableListOf<String>()
        var idx = text.indexOf("data:video/")
        while (idx >= 0) {
            val baseStart = text.indexOf("base64,", idx)
            if (baseStart < 0) break
            val prefix = text.substring(idx, baseStart + 7) // includes "base64,"

            // từ sau "base64," bắt đầu collect các ký tự base64, có thể bị ngắt bởi ', " hoặc + hoặc whitespace
            val sb = StringBuilder()
            var i = baseStart + 7
            var collected = 0
            val maxCollect = 300_000 // prevent runaway
            while (i < text.length && collected < maxCollect) {
                val c = text[i]
                // nếu là base64 char thì append
                if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '=') {
                    sb.append(c)
                    collected++
                    i++
                    continue
                }
                // nếu gặp quote or plus or whitespace, skip and continue collecting next base64 chunks
                if (c == '\'' || c == '\"' || c == '+' || c.isWhitespace()) {
                    i++
                    continue
                }
                // nếu gặp < or > or /script or other markup and already collected some base64 -> break
                if ((c == '<' || c == '>') && collected > 8) break
                // if punctuation not allowed and we already have enough -> break
                if (!c.isLetterOrDigit() && collected > 8) break
                i++
            }

            if (sb.isNotEmpty()) {
                val clean = prefix + sb.toString()
                out.add(clean)
            }

            // tìm next
            idx = text.indexOf("data:video/", i)
        }
        return out
    }

    tryReconstructDataUris(pageText).forEach { videoUrls.add(it) }

    // --- Debug logging để kiểm tra runtime (bật khi cần) ---
    Log.d("Base64Debug", "Collected videoUrls size=${videoUrls.size}")
    Log.d("Base64Debug", "Collected videoUrls sample=${videoUrls.take(10)}")

    // Xử lý tất cả URL đã thu thập
    videoUrls.toList().amap { url ->
        val ok = loadExtractor(
            url,
            referer = data,
            subtitleCallback = subtitleCallback
        ) { link ->
            callback(link)
        }
        if (ok) found = true
    }

    return found
}
}
