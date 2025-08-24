package com.Gaycock4U

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

class Gaycock4U : MainAPI() {
    override var mainUrl = "https://gaycock4u.com"
    override var name = "Gaycock4U"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "" to "Latest Updates",  
        "$mainUrl/studio/onlyfans/" to "Onlyfans",
        "$mainUrl/category/latino/" to "Latino",
        "$mainUrl/studio/rawfuckclub/" to "Raw Fuck Club",
        "$mainUrl/studio/ragingstallion/" to "Ragingstallion",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = when {
            request.data.startsWith("/category/") && page > 1 -> "$mainUrl/${request.data}page/$page/"
            page > 1 -> "$mainUrl/${request.data}page/$page/"
            else -> "$mainUrl/${request.data}"
        }

        val ua = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0")
        val document = app.get(url, headers = ua).document
        // Fixed selector - using correct container class
        val home = document.select("article.elementor-post").mapNotNull { it.toSearchResult() }

        // Fixed pagination detection
        val hasNext = document.selectFirst("a.page-numbers.next") != null

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
        val title = this.selectFirst("a.title")?.text()?.trim() ?: ""
        val href = this.selectFirst("a")?.attr("href")?.trim() ?: ""
        val posterUrl = this.selectFirst("a img")?.attr("src")?.trim() ?: ""
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("a.title")?.text()?.trim() ?: ""
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

            val results = document.select("article.elementor-post").mapNotNull { it.toSearchResult() }

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

        val recommendations = document.select("article.elementor-post").mapNotNull {
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
    // Header + request
    val headers = mapOf("User-Agent" to "Mozilla/5.0", "Referer" to data)
    val res = app.get(data, headers = headers)
    // Thường là res.document; nếu lib của bạn dùng res.doc thì đổi lại
    val doc = try {
        res.document
    } catch (e: Throwable) {
        // Nếu res.document không tồn tại, thử res.doc (nếu lib của bạn có)
        try {
            // dùng reflection fallback nếu muốn, hoặc thay bằng res.doc nếu chắc chắn
            @Suppress("UNCHECKED_CAST")
            val fallback = res::class.members.firstOrNull { it.name == "doc" || it.name == "document" }
                ?.call(res) as? org.jsoup.nodes.Document
            fallback
        } catch (_: Throwable) {
            null
        }
    } ?: return false // không lấy được HTML -> thoát

    val urlRegex = Regex("""https?://[^\s'"]+?\.(?:mp4|m3u8|webm)(\?[^'"\s<>]*)?""", RegexOption.IGNORE_CASE)
    val videoUrls = mutableListOf<String>()

    // 1) Thu thập từ iframe: ưu tiên data-src rồi src
    for (iframe in doc.select("iframe")) {
        val dataSrc = iframe.attr("data-src").takeIf { it.isNotBlank() }
        val src = dataSrc ?: iframe.attr("src").takeIf { it.isNotBlank() }
        if (src != null) videoUrls.add(src)
    }

    // 2) Tìm URL trực tiếp trong toàn bộ HTML (script, attribute, ...)
    urlRegex.findAll(doc.html()).forEach { match ->
        videoUrls.add(match.value)
    }

    val candidates = videoUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    val namePrefix = this.name

    for ((index, url) in candidates.withIndex()) {

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "$namePrefix ${index + 1}",
                url = url
            ) {
                this.referer = data
                this.quality = getQualityFromName(url) ?: Qualities.Unknown.value
                this.headers = headers
            }
        )
    }

    return true
}
}
