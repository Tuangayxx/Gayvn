package com.BestHDgayporn

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Element
import org.json.JSONObject

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
        val posterUrl = aTag.selectFirst(".aiovg-thumbnail img")?.attr("src")
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

suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val document = app.get(data).document
    val jsonLdScript = document.selectFirst("script[type=application/ld+json]")

    jsonLdScript?.let { script ->
        try {
            val jsonData = script.html().replace("\\/", "/")
            val jsonObject = JSONObject(jsonData)

            if (jsonObject.optString("@type") == "VideoObject") {
                val videoUrl = jsonObject.optString("contentUrl", "")
                
                if (videoUrl.isNotBlank() && videoUrl.endsWith(".mp4", ignoreCase = true)) {
                    val videoName = jsonObject.optString("name", "Direct Video")
                    
                    callback.invoke(
                        newExtractorLink(
                            source = "GaypornHDfree",
                            name = videoName,
                            url = videoUrl,
                            type = INFER_TYPE
                            ) {
                            this.referer = "mainUrl"
                        }
                    )
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return false
} }
