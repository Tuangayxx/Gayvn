package com.BestHDgayporn

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Element
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

class BestHDgayporn : MainAPI() {
    override var mainUrl = "https://besthdgayporn.com"
    override var name = "BestHDgayporn"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "" to "Latest",
        "/video-tag/onlyfans" to "Onlyfans",
        "/video-tag/men-com" to "MEN.com",
        "/video-tag/corbin-fisher" to "Corbin Fisher",
        "/video-tag/raw-fuck-club" to "Raw fuck club",
        "/video-tag/randy-blue" to "Randy Blue",
        "/video-tag/sean-cody" to "Sean Cody",
        "/video-tag/falcon-studios" to "Falcon Studio",
        "/video-tag/voyr" to "Voyr",
        "/video-tag/next-door-studios" to "Next Door Studios",
        "/video-tag/noir-male" to "Noir Male",
        "/video-tag/asg-max" to "ASG Max",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "${request.data}page/$page/"
        } else {
            request.data
        }

        val document = app.get(url).document
        val responseList = document.select("div.aiovg-item-video").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(request.name, responseList, isHorizontalImages = true),
            hasNext = responseList.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag  = this.selectFirst("a") ?: return null
        val href  = aTag.attr("href")
        val title = aTag.selectFirst("haiovg-link-title")?.text() ?: "No Title"

        var posterUrl = aTag.selectFirst(".post-thumbnail-container img")?.attr("data-src")
        if (posterUrl.isNullOrEmpty()) {
            posterUrl = aTag.selectFirst(".post-thumbnail-container img")?.attr("src")
        }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }


    // Search for videos
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        val items = document.select("div.aiovg-item-video")
        return items.mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val videoElement = doc.selectFirst("div[context='https:\/\/schema.org']")
            ?: throw ErrorLoadingException("Không tìm thấy thẻ video")

        val title = videoElement.selectFirst("meta[itemprop='name']")?.attr("content") ?: "No Title"
        val poster = videoElement.selectFirst("meta[itemprop='thumbnailUrl']")?.attr("content") ?: ""
        val description = videoElement.selectFirst("meta[itemprop='description']")?.attr("content") ?: ""

        val actors = doc.select("#video-actors a").mapNotNull { it.text() }.filter { it.isNotBlank() }

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
            val embedUrl = document.selectFirst("video")?.attr("src")

            if (!embedUrl.isNullOrEmpty()) {
                loadExtractor(embedUrl, data, subtitleCallback, callback)
            }

            return true
        }
    }
