package com.BestHDgayporn

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Element

class BestHDgayporn : MainAPI() {
    override var mainUrl = "https://besthdgayporn.com"
    override var name = "BestHDgayporn"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        ""                             to "Latest",
        "/video-tag/onlyfans"          to "Onlyfans",
        "/video-tag/men-com"           to "MEN.com",
        "/video-tag/corbin-fisher"     to "Corbin Fisher",
        "/video-tag/raw-fuck-club"     to "Raw fuck club",
        "/video-tag/randy-blue"        to "Randy Blue",
        "/video-tag/sean-cody"         to "Sean Cody",
        "/video-tag/falcon-studios"    to "Falcon Studio",
        "/video-tag/voyr"              to "Voyr",
        "/video-tag/next-door-studios" to "Next Door Studios",
        "/video-tag/noir-male"         to "Noir Male",
        "/video-tag/asg-max"           to "ASG Max",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${mainUrl.trimEnd('/')}/${request.data.trimStart('/')}/page/$page/"
        val document = app.get(url).document

        val videos = document.select("article.postbox").mapNotNull { it.toSearchResult() }
        val hasNext = document.selectFirst("a.next.page-numbers") != null

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = videos,
                isHorizontalImages = true
            ),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResults = mutableListOf<SearchResponse>()
        for (i in 1..5) {
            val document = app.get("$mainUrl/page/$i/?s=$query").document
            val results = document.select("article.postbox").mapNotNull { it.toSearchResult() }

            if (results.isEmpty()) break
            searchResults.addAll(results)
        }
        return searchResults
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "No Title"
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("#player source").forEach {
            val videoUrl = it.attr("src")
            if (videoUrl.isNotBlank()) {
                loadExtractor(videoUrl, subtitleCallback, callback)
            }
        }
        return true
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = selectFirst("a") ?: return null
        val title = selectFirst("h5.entry-title")?.text()?.trim() ?: return null
        val href = anchor.attr("href").let { fixUrl(it) }
        val poster = selectFirst("img")?.attr("abs:src")?.let { fixUrlNull(it) }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = poster
        }
    }
}
