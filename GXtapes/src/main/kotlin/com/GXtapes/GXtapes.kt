package com.GXtapes

import org.jsoup.nodes.Element
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.json.JSONObject
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink


class GXtapes : MainAPI() {
    private val globalTvType = TvType.NSFW
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
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
        val home     = document.select("ul.listing-tube li").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("img").attr("title")
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        println(posterUrl)
        return newMovieSearchResponse(title, href, TvType.Movie) {
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
    // Sửa selector để lấy poster trực tiếp từ meta og:image
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
                    callback.invoke(ExtractorLink(
                        name = "88z.io",
                        source = "Direct",
                        url = directUrl,
                        referer = mainUrl, // Đã sửa ở đây
                        quality = Qualities.Unknown.value,
                        isM3u8 = false
                    ))
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
