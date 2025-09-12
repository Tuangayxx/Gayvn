package com.Gaycock4U

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

class Gaycock4U : MainAPI() {
    override var mainUrl = "https://gaycock4u.com"
    override var name = "Gaycock4U"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0"
    private val headers = mapOf(
        "User-Agent" to userAgent,
        "Accept" to "*/*",
        "Connection" to "keep-alive",
        "Range" to "bytes=0-0",
        "X-Requested-With" to "XMLHttpRequest",
        "Accept-Language" to "en-US,en;q=0.9",
    )

    override val mainPage = mainPageOf(
        "" to "Latest Updates",
        "/category/amateur/" to "Amateur",
        "/category/bareback/" to "Bareback",
        "/category/bigcock/" to "Big Cock",
        "/category/group/" to "Group",
        "/category/hardcore/" to "Hardcore",
        "/category/latino/" to "Latino",
        "/category/interracial/" to "Interracial",
        "/category/twink/" to "Twink",
        "/studio/asianetwork/" to "Asianetwork",
        "/studio/bromo/" to "Bromo",
        "/studio/latinonetwork/" to "Latino Network",
        "/studio/lucasentertainment/" to "Lucas Entertainment",
        "/studio/onlyfans/" to "Onlyfans",
        "/studio/rawfuckclub/" to "Raw Fuck Club",
        "/studio/ragingstallion/" to "Ragingstallion",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "$mainUrl${request.data}page/$page"
        } else {
            "$mainUrl${request.data}"
        }

        val res = app.get(url, headers = headers, allowRedirects = true)
        val document = res.document
        // Fixed selector - using correct container class
        val home = document.select("div.elementor-widget-container article.elementor-post").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        // Fixed selectors to match actual HTML structure
        val title = this.selectFirst("p.elementor-heading-title a")?.text()?.trim() ?: ""
        val href = this.selectFirst("a")?.attr("href")?.trim() ?: ""
        val posterUrl = this.selectFirst("a img")?.attr("src")?.trim() ?: ""
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("p.elementor-heading-title a")?.text()?.trim() ?: ""
        val href = this.selectFirst("a")?.attr("href")?.trim() ?: ""
        val posterUrl = this.selectFirst("a img")?.attr("src")?.trim() ?: ""
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("div.elementor-widget-container article.elementor-post").mapNotNull { it.toSearchResult() }

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

        val recommendations = document.select("div.elementor-widget-container article.elementor-post").mapNotNull {
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
    var found = false

    fun normalize(u: String): String {
        val url = u.trim()
        return when {
            url.isEmpty() -> ""
            url.startsWith("//") -> "https:$url"
            else -> url
        }
    }

    document.select("iframe[src], iframe[data-src]").forEach { f ->
        val url = f.absUrl("src").ifBlank { f.attr("src") }
            .ifBlank { f.absUrl("data-src") }
            .ifBlank { f.attr("data-src") }
        if (url.isNotBlank()) {
            found = true
            loadExtractor(normalize(url), subtitleCallback, callback)
        }
    }

    return found
}
}
