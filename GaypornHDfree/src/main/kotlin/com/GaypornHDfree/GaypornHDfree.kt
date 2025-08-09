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
            ""                              to "Mới cập nhật",
            "category/onlyfans/"               to "Onlyfans",
            "category/gay-porn-movies/"        to "Phim dài",
            "category/asian-gay-porn-hd/"      to "Châu Á",
            "category/bilatinmen/"             to "La tin cu bự",
            "category/fraternityx/"            to "Fraternity X",
            "category/sketchysex/"             to "Sketchy Sex",
            "2025/07/"                         to "Video 7",
            "2025/06/"                         to "Video 6",
            "2025/05/"                         to "Video 5",
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val url = if (page == 1) "$mainUrl/${request.data}" else "$mainUrl/${request.data}page/$page/"

    val headers = mutableMapOf(
        "User-Agent" to "Mozilla/5.0",
        "Referer" to mainUrl
    )

    val document = app.get(url, referer = "$mainUrl/", cookies = cookies).document
    val home = document.select("div.videopost").mapNotNull { it.toSearchResult() }

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

private fun Element.toSearchResult(): SearchResponse? {
    val anchor = selectFirst("a.thumb-video") ?: return null
    val href = anchor.attr("href").trim().ifEmpty { return null }

    val title = selectFirst("div.deno.video-title a")?.text()?.trim().orEmpty()
    val posterUrl = selectFirst("a.thumb-video img")?.attr("src")?.trim().orEmpty()

    return newMovieSearchResponse(title, href, TvType.NSFW) {
        this.posterUrl = posterUrl
    }
}



    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("div.deno.video-title a")?.text()?.trim().orEmpty()
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
