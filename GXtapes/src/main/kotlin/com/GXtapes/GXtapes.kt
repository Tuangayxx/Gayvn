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
        "" to "Latest",
        "$mainUrl/category/hdporn-783479" to "Random",
        "$mainUrl/84750" to "Asian",
        "$mainUrl/latin-287326" to "Latino",
        "$mainUrl/68780" to "BelAmi",
        "$mainUrl/62478" to "FraternityX",
        "$mainUrl/416510" to "FalconStudio",
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> ede6fa2 (Update GXtapes.kt)
        "$mainUrl/category/416510/" to "Falcon Studio",
        "$mainUrl/627615/" to "Onlyfans",
        "$mainUrl/category/627615/" to "Only fans",
        "$mainUrl/category/37433/" to "Gay Hoopla",
<<<<<<< HEAD
=======
=======
>>>>>>> c31978e (Update GXtapes.kt)
=======
>>>>>>> c31978e (Update GXtapes.kt)
        "$mainUrl/category/416510" to "Falcon Studio",
        "$mainUrl/627615" to "Onlyfans",
        "$mainUrl/category/627615" to "Only fans",
        "$mainUrl/category/37433" to "Gay Hoopla",
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> c31978e (Update GXtapes.kt)
=======
>>>>>>> c31978e (Update GXtapes.kt)
=======
>>>>>>> c31978e (Update GXtapes.kt)
=======
>>>>>>> ede6fa2 (Update GXtapes.kt)
    )    

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
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
        document.select("#video-code iframe").forEach { links ->
            val url = links.attr("src")
            Log.d("Tuangayxx Test", url)
            loadExtractor(url, subtitleCallback, callback)
        }
        return true
    }
}
