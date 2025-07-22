package com.Nurgay

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Element
import java.io.IOException
import com.lagradost.api.Log


class Nurgay : MainAPI() {
    override var mainUrl = "https://nurgay.to"
    override var name = "Nurgay"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        "?filter=latest"                         to "Latest",
        "?filter=random"                         to "Random",
        "?filter=most-viewed"                    to "Most Viewed",
        "asiaten"                                to "Asian",
        "gruppensex"                             to "Group Sex",
    )    

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page == 1) "$mainUrl/${request.data}" else
                                     "$mainUrl$/page/$page/${request.data}"
        val document = app.get(pageUrl).document
        val home = document.select("div.videos-list").mapNotNull { it.toSearchResult() }

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
        val posterUrl = fixUrlNull(this.select("img").attr("data-src"))
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

   override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("div.videos-list").mapNotNull { it.toSearchResult() }

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

        val supportedDomains = listOf(
            "bigwarp.io", "voe.sx", "mixdrop", 
            "streamtape", "doodstream.com", "abyss.to", "vinovo.to",
            "vide0.net",  // Thêm domain Doodstream thực tế Thêm domain download
        )
        
        val document = app.get(data).document

        // Lấy tất cả link hợp lệ trong cả 2 khu vực:
        val links = document.select("""
            div.notranslate a[href],
            div.desc a[href]
        """).mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
            .filter { url -> 
                supportedDomains.any { domain -> domain in url } 
            }
            .distinct()

        if (links.isEmpty()) return false

        links.forEach { url ->
            Log.i("Gayxx", "Processing URL: $url")
            loadExtractor(url, subtitleCallback, callback)
        }
        return true
    }
}
