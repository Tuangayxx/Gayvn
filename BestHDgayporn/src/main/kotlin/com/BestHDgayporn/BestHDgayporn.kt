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
        val document = app.get(url).document
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
        // Lấy poster theo cách tương tự Fxggxt.kt:
        var posterUrl = aTag.selectFirst(".post-thumbnail-container img")?.attr("data-src")
        if (posterUrl.isNullOrEmpty()) {
            posterUrl = aTag.selectFirst(".post-thumbnail-container img")?.attr("src")
        }
        val title = aTag.selectFirst("haiovg-link-title")?.text()?.trim() ?: "No Title"
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
        val doc = app.get(url).document
        val videoElement = doc.selectFirst("div[context='https://schema.org']")
            ?: throw ErrorLoadingException("Không tìm thấy thẻ video")
        val title = videoElement.selectFirst("meta[itemprop='name']")?.attr("content") ?: "No Title"
        val poster = videoElement.selectFirst("meta[itemprop='thumbnailUrl']")?.attr("content") ?: ""
        val description = videoElement.selectFirst("meta[itemprop='description']")?.attr("content") ?: ""
        val actors = doc.select("#video-actors a")
            .mapNotNull { it.text().trim() }
            .filter { it.isNotEmpty() }
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
                document.select("video[src]").forEach {
            val url = it.attr("src")
        loadExtractor(url, subtitleCallback, callback)
    }
    return true
}
}
