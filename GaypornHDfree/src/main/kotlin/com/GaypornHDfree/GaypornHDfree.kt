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

    private val cloudflareKiller = CloudflareKiller()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0"
    }

    private val webViewResolver = WebViewResolver(
        userAgent = USER_AGENT,
        interceptUrl = Regex(".*\\.gaypornhdfree\\.com/.*|.*cloudflare.*")
    )

    val requestInterceptors: List<Interceptor> = listOf(
        webViewResolver,
        cloudflareKiller
    )

    private val headers = mapOf("User-Agent" to USER_AGENT)

    override val mainPage = mainPageOf(
        "/2025/" to "Latest Updates",
        "/2025/07/" to "July",
        "/2025/06/" to "June",
        "/2025/05/" to "May",
        "/2025/04/" to "April",     
        "/category/onlyfans/" to "Onlyfans",
        "/category/movies/" to "Movies",
        "/category/asian-gay-porn-hd/" to "Asian",
        "/category/western-gay-porn-hd/" to "Western",
        "/category/%e3%82%b2%e3%82%a4%e9%9b%91%e8%aa%8c/" to "Magazines",
        "/category/hunk-channel/" to "Hunk Channel",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = when {
            request.data.startsWith("/category/") && page > 1 -> "$mainUrl${request.data}page/$page/"
            page > 1 -> "$mainUrl${request.data}page/$page/"
            else -> "$mainUrl${request.data}"
        }

        val document = app.get(url, headers = headers).document
        val home = document.select("div.videopost").mapNotNull { it.toSearchResult() }
        val hasNext = document.select("a.next.page-numbers").isNotEmpty()

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
        val title = this.selectFirst("div.deno.video-title a")?.text()?.trim() ?: ""
        val href = fixUrl(this.selectFirst("a.thumb-video")?.attr("href") ?: "")
        val poster = this.selectFirst("img")?.let { 
            it.attr("src").ifEmpty { it.attr("data-src") } 
        } ?: ""

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        val seenUrls = mutableSetOf<String>()

        for (i in 1..5) {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val document = app.get("$mainUrl/page/$i/?s=$encodedQuery", headers = headers).document
            val results = document.select("div.videopost").mapNotNull { it.toSearchResult() }
                .filterNot { seenUrls.contains(it.url) }

            if (results.isEmpty()) break

            results.forEach { seenUrls.add(it.url) }
            searchResponse.addAll(results)
        }

        return searchResponse
    }
       
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = headers).document

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: ""
        val poster = document.selectFirst("meta[property='og:image']")?.attr("content") ?: ""
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val recommendations = document.select("div.videopost").mapNotNull { 
            it.toSearchResult() 
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
        val document = app.get(data, headers = headers).document
        val videoUrls = mutableSetOf<String>()

        // Extract from iframes
        document.select("iframe").forEach { iframe ->
            iframe.attr("data-src").takeIf { it.isNotBlank() }?.let(videoUrls::add)
            iframe.attr("src").takeIf { it.isNotBlank() }?.let(videoUrls::add)
        }

        // Extract from video players
        document.select("div.video-player[data-src]").forEach {
            it.attr("data-src").takeIf(String::isNotBlank)?.let(videoUrls::add)
        }

        // Extract from download buttons
        document.select("div.download-button-wrapper a[href]").forEach {
            it.attr("href").takeIf(String::isNotBlank)?.let(videoUrls::add)
        }

        videoUrls.forEach { url ->
            val fixedUrl = when {
                url.startsWith("http") -> url
                url.startsWith("//") -> "https:$url"
                else -> "$mainUrl/${url.removePrefix("/")}"
            }
            loadExtractor(fixedUrl, subtitleCallback, callback)
        }

        return videoUrls.isNotEmpty()
    }
}
