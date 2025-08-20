package com.BestHDgayporn

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.nodes.Element
import org.json.JSONObject
import org.json.JSONArray
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BestHDgayporn : MainAPI() {
    override var mainUrl = "https://besthdgayporn.com"
    override var name = "BestHDgayporn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest",
        "$mainUrl/video-tag/men-com/" to "MEN.com",
        "$mainUrl/video-tag/bareback-gay-porn/" to "Bareback",
        "$mainUrl/video-tag/onlyfans/" to "Onlyfans"
    )

    private val cookies = mapOf(Pair("hasVisited", "1"), Pair("accessAgeDisclaimerPH", "1"))

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else request.data
        val ua = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0")
        val document = app.get(url, headers = ua).document
        val home = document.select("div.aiovg-item-video").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = this.selectFirst("a") ?: return null
        val href = aTag.attr("href")
        val posterUrl = this.select("img").attr("src")
        val title = this.selectFirst(".aiovg-link-title")?.text()?.trim() ?: "No Title"

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        val items = document.select("div.aiovg-item-video")
        return items.mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val ua = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0")
        val doc = app.get(url, headers = ua).document

        val title = doc.selectFirst("meta[property='og:title']")?.attr("content") ?: doc.title()

        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content") ?: ""

        val description = doc.selectFirst("meta[property='og:description']")?.attr("content") ?: ""

        val actors = listOf("Flynn Fenix", "Nicholas Ryder").filter { title.contains(it) }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            if (actors.isNotEmpty()) addActors(actors)
        }
    }

    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val document = app.get(data).document
    
    // First try to find direct video source
    val directVideoUrl = document.selectFirst("video#player_html5_api")?.attr("src")
    if (!directVideoUrl.isNullOrEmpty()) {
        callback.invoke(
            newExtractorLink(
                name = this.name,
                source = this.name,
                url = directVideoUrl,
                type = INFER_TYPE
                    ) {
                this.referer = mainUrl
            }
        )
        
        return true
    }

    // If no direct video found, try to find iframe source
    val iframeSrc = document.selectFirst("div.aiovg-player iframe")?.attr("src")
    if (!iframeSrc.isNullOrEmpty()) {
        // If iframe found, get its content and look for video
        val iframeDoc = app.get(iframeSrc).document
        val videoUrl = iframeDoc.selectFirst("video#player_html5_api")?.attr("src")
        
        if (!videoUrl.isNullOrEmpty()) {
            callback.invoke(
            newExtractorLink(
                name = this.name,
                source = this.name,
                url = videoUrl,
                type = INFER_TYPE
                    ) {
                this.referer = mainUrl
            }
        )   
        return true
    }
    }

    // Fallback: look for JSON-LD data containing video URL
    document.select("script[type=application/ld+json]").firstOrNull()?.data()?.let { jsonData ->
        val contentUrl = Regex("\"contentUrl\"\\s*:\\s*\"([^\"]+)\"").find(jsonData)?.groupValues?.get(1)
        if (!contentUrl.isNullOrEmpty()) {
            callback.invoke(
            newExtractorLink(
                name = this.name,
                source = this.name,
                url = contentUrl,
                type = INFER_TYPE
                    ) {
                this.referer = mainUrl
            }
        )
        
        return true
    }
    }
        return false
}
}
