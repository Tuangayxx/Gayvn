package com.Gayxx

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Element
import java.io.IOException
import com.lagradost.api.Log


class Gayxx : MainAPI() {
    override var mainUrl = "https://gayxx.net"
    override var name = "Gayxx"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        "/?type=latest" to "Latest",
        "/category/asian-gay-sex" to "Onlyfans",
        "/category/group" to "Càng đông càng vui",
        "/hottest-gay-porn-sex" to "Hot",
        "/category/sex-gay-onlyfans-chinese" to "Chinese",
    )    

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
        val home = document.select("div.videopost").mapNotNull { it.toSearchResult() }

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
        val title = this.select("a").attr("title")
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

            val results = document.select("div.videopost").mapNotNull { it.toSearchResult() }

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
        document.select(".videohere iframe[src]").forEach { links ->
            val url = links.attr("src")
            if (
                url.contains("vide0.net") ||
                url.contains("voe.sx") ||
                url.contains("mixdrop") ||
                url.contains("streamtape") ||
                url.contains("dood") ||
                url.contains("abyss")
            ) {
                Log.d("Tuangayxx Test", url)
                loadExtractor(url, subtitleCallback, callback)
            }
        }
        return true
    }
}
