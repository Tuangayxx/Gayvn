package com.Fxggxt

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.api.Log
import org.jsoup.nodes.Element
import org.json.JSONObject
import org.json.JSONArray
import org.jsoup.nodes.Document

class Fxggxt : MainAPI() {
    override var mainUrl = "https://fxggxt.com"
    override var name = "Fxggxt"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "$mainUrl/?filter=latest/"             to "Latest",
        "$mainUrl/tag/amateur-gay-porn/"       to "Amateur",
        "$mainUrl/tag/bareback-gay-porn/"      to "Bareback",
        "$mainUrl/tag/big-dick-gay-porn/"      to "Big Dick",
        "$mainUrl/tag/bisexual-porn/"          to "Bisexual",
        "$mainUrl/tag/group-gay-porn/"         to "Group",
        "$mainUrl/tag/hunk-gay-porn-videos/"   to "Hunk",
        "$mainUrl/tag/interracial-gay-porn/"   to "Interracial",
        "$mainUrl/tag/muscle-gay-porn/"        to "Muscle",
        "$mainUrl/tag/straight-guys-gay-porn/" to "Straight",
        "$mainUrl/tag/twink-gay-porn/"         to "Twink",
        "$mainUrl/category/adulttime/"         to "Adulttime",
        "$mainUrl/category/asgmax/"            to "ASGmax",
        "$mainUrl/category/bel-ami/"           to "Bel Ami",
        "$mainUrl/category/breederbros/"       to "Breeder Bros",
        "$mainUrl/category/clubbangboys/"      to "Club Bang Boys",
        "$mainUrl/category/cocksuremen/"       to "Cock Sure Men",
        "$mainUrl/category/cocky-boys/"        to "Cocky Boys",
        "$mainUrl/category/corbin-fisher/"     to "Corbin Fisher",
        "$mainUrl/category/creamybros/"        to "Creamy Bros",
        "$mainUrl/category/cumdumpsluts/"      to "Cumdumpsluts",
        "$mainUrl/category/ericvideos/"        to "Eric videos",
        "$mainUrl/category/facedownassup/"          to "Face down ass up",
        "$mainUrl/category/fraternity-x/"           to "Fraternity X",
        "$mainUrl/category/just-for-fans/"          to "Just For Fans",
        "$mainUrl/category/kristenbjorn/"           to "Kristen Bjorn",
        "$mainUrl/category/bi-guys-fuck/"           to "Bi Guys Fuck",
        "$mainUrl/category/falcon-studios/"         to "Falcon Studios",
        "$mainUrl/category/treasure-island-media/"  to "Treasure Island Media",
        "$mainUrl/category/slamrush/"               to "Slam Rush",
        "$mainUrl/category/seehimfuck/"             to "See Him Fuck",
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "${request.data}page/$page/"
        } else {
            request.data
        }

        val document = app.get(url).document
        val responseList = document.select("article.loop-video.thumb-block").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(request.name, responseList, isHorizontalImages = true),
            hasNext = responseList.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = this.selectFirst("a") ?: return null
        val href = aTag.attr("href")
        val title = aTag.selectFirst("header.entry-header span")?.text() ?: "No Title"

        var posterUrl = aTag.selectFirst(".post-thumbnail-container img")?.attr("data-src")
        if (posterUrl.isNullOrEmpty()) {
            posterUrl = aTag.selectFirst(".post-thumbnail-container img")?.attr("src")
        }

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val aTag = this.selectFirst("a") ?: return null
        val href = aTag.attr("href")
        val title = aTag.selectFirst("header.entry-header span")?.text() ?: "No Title"

        var posterUrl = aTag.selectFirst(".post-thumbnail-container img")?.attr("data-src")
        if (posterUrl.isNullOrEmpty()) {
            posterUrl = aTag.selectFirst(".post-thumbnail-container img")?.attr("src")
        }

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..5) {
            val url = if (i > 1) {
                "$mainUrl/page/$i/?s=$query"
            } else {
                "$mainUrl/?s=$query"
            }

            val document = app.get(url).document
            val results = document.select("article.loop-video.thumb-block").mapNotNull { it.toSearchResult() }

            if (results.isEmpty()) break
            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
    val doc = app.get(url).document
    val videoElement = doc.selectFirst("article[itemtype='http://schema.org/VideoObject']")
        ?: throw ErrorLoadingException("Không tìm thấy thẻ video")

    val title = videoElement.selectFirst("meta[itemprop='name']")?.attr("content") ?: "No Title"
    val poster = videoElement.selectFirst("meta[itemprop='thumbnailUrl']")?.attr("content") ?: ""
    val description = videoElement.selectFirst("meta[itemprop='description']")?.attr("content") ?: ""

    val actors = doc.select("#video-actors a").mapNotNull { it.text() }.filter { it.isNotBlank() }

    val recommendations = doc.select("article.loop-video.thumb-block").mapNotNull {
            it.toRecommendResult()
    }

    return newMovieLoadResponse(title, url, TvType.NSFW, url) {
        this.posterUrl = poster
        this.plot = description
        if (actors.isNotEmpty()) addActors(actors)
        this.recommendations = recommendations
    }
}


    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
     
        // Headers tương thích TV
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Origin" to mainUrl,
            "Referer" to referer ?: mainUrl,
            "Access-Control-Allow-Origin" to "*"
    )
    
        val document = app.get(data, headers = headers).document
    
            document.select("div.responsive-player iframe[src]").forEach {
        val url = it.attr("src")
            loadExtractor(url, subtitleCallback, callback)
        }
            return true
    }
}

