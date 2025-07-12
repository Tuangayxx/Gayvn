package com.GXtapes

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.ExtractorLinkType


class GXtapes : MainAPI() {
    override var mainUrl = "https://g.xtapes.in"
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
        "/68780" to "BelAmi",
        "/62478" to "FraternityX",
        "/416537" to "Falcon Studio",
        "/627615" to "Onlyfans",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.isEmpty()) {
            "$mainUrl/page/$page/"
        } else {
            "$mainUrl${request.data}/page/$page/"
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

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("$mainUrl/page/$i/?s=$query").document
            val results = document.select("ul.listing-tube li").mapNotNull { it.toSearchResult() }

            if (results.isEmpty()) break
            
            results.forEach { newItem ->
                if (searchResponse.none { it.url == newItem.url }) {
                    searchResponse.add(newItem)
                }
            }
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
        var found = false

        document.select("#video-code iframe").forEach { iframe ->
            val src = iframe.attr("src")
            when {
                src.contains("74k.io") -> {
                    val decodedUrl = "https://74k.io/e/" + src.substringAfterLast("/")
                    found = found or loadExtractor(decodedUrl, subtitleCallback, callback)
                }
                src.contains("88z.io") -> {
                    val videoHash = src.substringAfter("#")
                    val directUrl = "https://88z.io/getvid/$videoHash"
                    
                    // Sử dụng constructor chính xác
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = "88z.io",
                            url = directUrl,
                            referer = mainUrl,
                            quality = Qualities.Unknown.value,
                            type = ExtractorLinkType.VIDEO // Thêm type ở đây
                        )
                    )
                    found = true
                }
                else -> {
                    found = found or loadExtractor(src, subtitleCallback, callback)
                }
            }
        }

        return found
    }
}
