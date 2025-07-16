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
        // Build URL cho "Latest" hoặc category
        val url = if (request.data.isNullOrBlank()) {
            "$mainUrl/page/$page/"
        } else {
            "$mainUrl${request.data.trimStart('/')}/page/$page/"
        }

        val document = app.get(url).document

        // Chọn đúng các item-video của aiovg
        val items = document.select("div.aiovg-section-videos div.aiovg-item-video")
        val videos = items.mapNotNull { it.toSearchResult() }
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
        val results = mutableListOf<SearchResponse>()
        for (i in 1..5) {
            val doc = app.get("$mainUrl/page/$i/?s=$query").document
            val pageItems = doc.select("div.aiovg-section-videos div.aiovg-item-video")
            val pageResults = pageItems.mapNotNull { it.toSearchResult() }
            if (pageResults.isEmpty()) break
            results += pageResults
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val d = app.get(url).document
        val title = d.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
        val poster = d.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        val plot   = d.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val d = app.get(data).document
        d.select("#player source").forEach {
            val src = it.attr("src")
            if (src.isNotBlank()) loadExtractor(src, subtitleCallback, callback)
        }
        return true
    }

    // Chuyển mỗi aiovg-item-video thành SearchResponse
    private fun Element.toSearchResult(): SearchResponse? {
        // Lấy link & title từ caption
        val linkElem = selectFirst("div.aiovg-caption a.aiovg-link-title")
            ?: return null
        val href  = fixUrl(linkElem.attr("href"))
        val title = linkElem.text().trim()
        // Lấy poster từ img[data-src] (lazy-load)
        val imgElem = selectFirst("div.aiovg-thumbnail img")
        val dataSrc = imgElem?.attr("data-src").takeIf { !it.isNullOrBlank() }
            ?: imgElem?.attr("src").orEmpty()
        val poster = fixUrlNull(dataSrc)

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            posterUrl = poster
        }
    }
}
