package com.GaypornHDfree

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

class GaypornHDfree : MainAPI() {
    override var mainUrl              = "https://gaypornhdfree.com"
    override var name                 = "GaypornHDfree"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    val cookies = mapOf("i18next" to "en")
        
    override val mainPage = mainPageOf(
            "/?s="                              to "Mới cập nhật",
            "/category/onlyfans/"               to "Onlyfans",
            "/category/gay-porn-movies/"        to "Phim dài",
            "/category/asian-gay-porn-hd/"      to "Châu Á",
            "/category/bilatinmen/"             to "La tin cu bự",
            "/category/fraternityx/"            to "Fraternity X",
            "/category/sketchysex/"             to "Sketchy Sex",
            "/2025/07/"                         to "Video 7",
            "/2025/06/"                         to "Video 6",
            "/2025/05/"                         to "Video 5",
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val url = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}page/$page/"

    // Base headers
    val headers = mutableMapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0",
        "Referer" to mainUrl
    )

    var document = app.get(url, headers = headers).document

    // Nếu server trả age-gate overlay, thử bypass bằng cookie (age_gate=1) và request lại
    if (document.selectFirst(".age-gate") != null || document.selectFirst("html.age-gate__restricted") != null) {
        // Thử set cookie đơn giản trước (nhiều site chấp nhận age_gate=1)
        headers["Cookie"] = "age_gate=1"
        document = app.get(url, headers = headers).document

        // Nếu vẫn còn age-gate, bạn có thể thử gọi endpoint check (tuỳ hỗ trợ của app.post)
        if (document.selectFirst(".age-gate") != null) {
            try {
                // Gửi POST tới endpoint để set cookie (tuỳ api của client: app.post(...) )
                val postHeaders = headers.toMutableMap()
                postHeaders["Content-Type"] = "application/x-www-form-urlencoded"
                // body: age_gate[confirm]=1 (form-encoded). Điều này mô phỏng nút "Yes".
                app.post("$mainUrl/wp-json/age-gate/v3/check", data = "age_gate%5Bconfirm%5D=1", headers = postHeaders)
                // rồi request lại với cookie
                headers["Cookie"] = "age_gate=1"
                document = app.get(url, headers = headers).document
            } catch (e: Exception) {
                // ignore — fallback tiếp
            }
        }
    }

    // Lấy danh sách video bằng selector chính xác
    val home = document.select("div.videopost").mapNotNull { it.toSearchResult() }

    // Pagination: check anchor 'next' hoặc link rel=next
    val hasNext = document.selectFirst("a.next.page-numbers") != null ||
                  document.selectFirst("link[rel=next]") != null

    return newHomePageResponse(
        list = HomePageList(
            name = request.name,
            list = home,
            isHorizontalImages = true
        ),
        hasNext = hasNext
    )
}

private fun Element.toSearchResult(): SearchResponse? {
    val anchor = this.selectFirst("a.thumb-video") ?: return null
    val href = anchor.attr("href").trim().ifEmpty { return null }

    val title = this.selectFirst("div.deno.video-title a")?.text()?.trim().ifEmpty {
        anchor.attr("title")?.trim() ?: ""
    }

    val img = anchor.selectFirst("img")
    val posterUrl = img?.attr("data-src")?.takeIf { it.isNotBlank() }
        ?: img?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
        ?: img?.attr("src")?.trim().orEmpty()

    return newMovieSearchResponse(title, href, TvType.NSFW) {
        this.posterUrl = posterUrl
    }
}


    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("div.deno.video-title a")?.text()?.trim() ?: ""
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

            val results = document.select("div.videopost").mapNotNull { it.toSearchResult() }

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

        val recommendations = document.select("div.videopost").mapNotNull {
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
    val document = app.get(data).document
    val videoUrls = mutableSetOf<String>()

    // Thu thập URL từ iframe (ưu tiên data-src trước, fallback sang src)
    document.select("iframe").forEach { iframe ->
        iframe.attr("data-src").takeIf { it.isNotBlank() }?.let(videoUrls::add)
            ?: iframe.attr("src").takeIf { it.isNotBlank() }?.let(videoUrls::add)
    }

    // Thu thập URL từ player
    document.select("div.video-player[data-src]").forEach {
        it.attr("data-src").takeIf { src -> src.isNotBlank() }?.let(videoUrls::add)
    }

    // Thu thập URL từ download button
    document.select("div.download-button-wrapper a[href]").forEach {
        it.attr("href").takeIf { href -> href.isNotBlank() }?.let(videoUrls::add)
    }

    // Xử lý tất cả URL đã thu thập
    videoUrls.forEach { url ->
        loadExtractor(url, subtitleCallback, callback)
    }

    return videoUrls.isNotEmpty()
    }
}
