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

class GaypornHDfree : MainAPI() { // Sửa dấu hai chấm sau tên class
    override var mainUrl = "https://gaypornhdfree.com" // Thêm dấu ngoặc kép
    override var name = "GaypornHDfree" // Thêm dấu ngoặc kép
    override val hasMainPage = true
    override var lang = "en" // Thêm dấu ngoặc kép
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    private val cloudflareKiller = CloudflareKiller()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0" // Thêm dấu ngoặc kép
    }

    private val webViewResolver = WebViewResolver(
        userAgent = USER_AGENT,
        interceptUrl = Regex(".*gaypornhdfree\\.com.*cloudflare.*") // Sửa regex
    )

    override val requestInterceptors: List<Interceptor> = listOf( // Thêm kiểu dữ liệu
        webViewResolver,
        cloudflareKiller
    )

    private val headers = mapOf("User-Agent" to USER_AGENT) // Thêm dấu ngoặc kép

    override val mainPage = mainPageOf(
        "2025" to "Latest Updates", // Thêm dấu ngoặc kép
        "202507" to "July",
        "202506" to "June",
        "202505" to "May",
        "202504" to "April",     
        "category/onlyfans" to "Onlyfans", // Thêm dấu ngoặc kép và sửa đường dẫn
        "category/movies" to "Movies",
        "category/asian-gay-porn-hd" to "Asian",
        "category/western-gay-porn-hd" to "Western",
        "category/%e3%82%b2%e3%82%a4%e9%9b%91%e8%aa%8c" to "Magazines",
        "category/hunk-channel" to "Hunk Channel",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse { // Sửa kiểu trả về
        val url = when {
            request.data.startsWith("category") && page > 1 -> "$mainUrl/${request.data}/page/$page" // Sửa điều kiện
            page > 1 -> "$mainUrl/${request.data}/page/$page"
            else -> "$mainUrl/${request.data}"
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

    private fun Element.toSearchResult(): SearchResponse? { // Sửa kiểu trả về nullable
        val titleElement = this.selectFirst("div.deno.video-title a") ?: return null
        val title = titleElement.text().trim()  
        val href = fixUrl(titleElement.attr("href")) // Sửa selector an toàn
        
        val img = this.selectFirst("a.thumb-video img") ?: return null
        val poster = img.attr("src").ifEmpty { img.attr("data-src") } // Sửa cú pháp truy cập

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> { // Sửa kiểu trả về
        val searchResponse = mutableListOf<SearchResponse>() // Thêm kiểu dữ liệu
        val seenUrls = mutableSetOf<String>() // Thêm kiểu dữ liệu

        for (i in 1..5) {
            val encodedQuery = URLEncoder.encode(query, "UTF-8") // Thêm dấu ngoặc kép
            val document = app.get("$mainUrl/?s=$encodedQuery&page=$i", headers = headers).document // Sửa URL
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

        val title = document.selectFirst("meta[property='og:title']")?.attr("content")?.trim() ?: "" // Sửa selector
        val poster = document.selectFirst("meta[property='og:image']")?.attr("content") ?: "" // Sửa selector
        val description = document.selectFirst("meta[property='og:description']")?.attr("content")?.trim() ?: "" // Sửa selector

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
            iframe.attr("data-src").takeIf { it.isNotBlank() }?.let { videoUrls.add(it) }
            iframe.attr("src").takeIf { it.isNotBlank() }?.let { videoUrls.add(it) }
        }

        // Extract from video players
        document.select("div.video-player[data-src]").forEach {
            it.attr("data-src").takeIf { src -> src.isNotBlank() }?.let { videoUrls.add(it) }
        }

        // Extract from download buttons
        document.select("div.download-button-wrapper a[href]").forEach {
            it.attr("href").takeIf { href -> href.isNotBlank() }?.let { videoUrls.add(it) }
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