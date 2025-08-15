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
import com.lagradost.cloudstream3.network.WebViewResolver

class GaypornHDfree : MainAPI() {
    override var mainUrl = "https://gaypornhdfree.com"
    override var name = "GaypornHDfree"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    private val cloudflareKiller = CloudflareKiller()

    override suspend fun bypassAntiBot(url: String, method: String, headers: Map<String, String>, data: Map<String, String>): Response? {
        return cloudflareKiller.bypass(url, method, headers, data)
    }

    private val webViewResolver = WebViewResolver(
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0",
        interceptUrls = listOf(
            Regex(".*\\.gaypornhdfree\\.com/.*"),
            Regex(".*cloudflare.*")
        )
    )

    override val requestInterceptors: List<Interceptor> = listOf(
        webViewResolver,
        cloudflareKiller
    )

    override val mainPage = mainPageOf(
        "/2025/" to "Latest Updates",
        "/2025/07/" to "7",
        "/2025/06/" to "6",
        "/2025/05/" to "5",
        "/2025/04/" to "4",     
        "/category/onlyfans/" to "Onlyfans",
        "/category/movies/" to "Movies",
        "/category/asian-gay-porn-hd/" to "Châu Á",
        "/category/western-gay-porn-hd/" to "Châu Mỹ Âu",
        "/category/%e3%82%b2%e3%82%a4%e9%9b%91%e8%aa%8c/" to "Tạp chí",
        "/category/hunk-channel/" to "Hunk Channel",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = when {
            request.data.startsWith("/category/") && page > 1 -> "$mainUrl${request.data}page/$page/"
            page > 1 -> "$mainUrl${request.data}page/$page/"
            else -> "$mainUrl${request.data}"
        }

        val ua = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0")
        
        // Sử dụng requestInterceptor để xử lý Cloudflare
        val document = app.get(
            url, 
            headers = ua,
            interceptor = requestInterceptors // Áp dụng interceptors
        ).document

        // Fixed selector - using correct container class
        val home = document.select("div.videopost").mapNotNull { it.toSearchResult() }

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
        val title = this.selectFirst("div.deno.video-title a")?.text()?.trim() ?: ""
        val href = this.selectFirst("a.thumb-video")?.attr("href")?.trim() ?: ""
        val posterUrl = selectFirst("a.thumb-video img")
            ?.let { it.attr("src").ifEmpty { it.attr("data-src") } }

        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("div.deno.video-title a")?.text()?.trim() ?: ""
        val href = this.selectFirst("a.thumb-video")?.attr("href")?.trim() ?: ""
        val posterUrl = selectFirst("a.thumb-video img")
            ?.let { it.attr("src").ifEmpty { it.attr("data-src") } }
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/page/$i/?s=$query", interceptor = requestInterceptors).document

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
