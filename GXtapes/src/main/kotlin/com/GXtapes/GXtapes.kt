package com.GXtapes

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Element
import java.io.IOException
import com.lagradost.api.Log


class GXtapes : MainAPI() {
    override var mainUrl = "https://gay.xtapes.in"
    override var name = "G_Xtapes"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        "/?filtre=date&cat=0"                     to "Latest",
        "/category/porn-movies-214660"            to "Full Movies",
        "/category/groupsex-gangbang-porn-189457" to "Gang bang & Group",
        "/category/860425"                        to "Corbin Fisher",
        "/category/139616"                        to "Timtales",
        "/category/687469"                        to "Bel Ami",
        "/category/651571"                        to "Broke Straight Boys",
        "/category/850356"                        to "BroMo",
        "/category/847926"                        to "CockyBoys",
        "/category/346893"                        to "Sean Cody",
        "/category/62478"                         to "Fraternity X",
        "/category/416510"                        to "Falcon Studio",
        "/category/37433"  to "Gay Hoopla",
        "/category/621537" to "Onlyfans",
    )    

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "/page/$page${request.data}"
        } else {
            request.data
        }

        val document = app.get(url).document
        val home = document.select("ul.listing-tube li").mapNotNull { it.toSearchResult() }

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
        val title = this.select("img").attr("title")
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toRecommendResult(): SearchResponse {
        val title = this.select("img").attr("title")
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

   override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("ul.listing-tube li").mapNotNull { it.toSearchResult() }

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

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: ""
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val recommendations = document.select("ul.listing-tube li").mapNotNull {
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
        document.select("#video-code iframe").forEach { links ->
            val url = links.attr("src")
            Log.d("Tuangayxx Test", url)
            loadExtractor(url, subtitleCallback, callback)
        }
        return true
    }
}
