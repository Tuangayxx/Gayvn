package com.Jayboys

import android.util.Log
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import org.jsoup.Jsoup
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.FormBody

class Jayboys : MainAPI() {
    override var mainUrl              = "https://javboys.tv"
    override var name                 = "Javboys"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded
    
    val subtitleCatUrl = "https://www.subtitlecat.com"
    
    override val mainPage = mainPageOf(
            "/2025/"                                          to "Latest Upadates",
            "/category/onlyfans/"                             to "Onlyfans",
            "/category/movies/"                               to "Movies",
            "/category/asian-gay-porn-hd/"                    to "Châu Á",
            "/category/western-gay-porn-hd/"                  to "Châu Mỹ Âu",
            "/category/%e3%82%b2%e3%82%a4%e9%9b%91%e8%aa%8c/" to "Tạp chí",
            "/category/hunk-channel/"                         to "Hunk Channel",
        )
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            val document = if(page == 1)
            {
                app.get("$mainUrl${request.data}").document
            }
            else
            {
                app.get("$mainUrl${request.data}recent/$page").document
            }

            val responseList  = document.select("div.video.col-2").mapNotNull { it.toSearchResult() }
            return newHomePageResponse(HomePageList(request.name, responseList, isHorizontalImages = false),hasNext = true)

    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("span.title").text()
        val href =  this.select("a.thumb-video").attr("href")
        val posterUrl = this.selectFirst("a.thumb-video img")?.attr("src")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..7) {
            val document = app.get("$mainUrl/search/video/?s=$query&page=$i").document
            //val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("div.video.col-2").mapNotNull { it.toSearchResult() }

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

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
    

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    import org.jsoup.Jsoup

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val doc = Jsoup.parse(data)
    var found = false
    
    // Lấy tất cả player có class 'video-player'
    doc.select("div.video-player").forEach { player ->
        // Lấy link từ thuộc tính data-src
        val videoUrl = player.attr("data-src").takeIf { it.isNotBlank() }
        
        videoUrl?.let { url ->
            found = true
            // Gọi hàm xử lý link (giữ nguyên logic gốc)
            loadExtractor(url, subtitleCallback, callback)
        }
    }
    
    return found
}
}

        