package com.Icegay

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Element
import org.json.JSONArray


class Icegay : MainAPI() {
    override var mainUrl = "https://www.boyfriendtv.com"
    override var name = "Boyfriendtv"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
            ""                          to "Home",
            "/search/?q=Vietnamese"     to "Newest",
            "/search/?q=asian&hot="      to "Asian",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}?page=$page"
        val document = app.get(pageUrl).document

        val items = document.select("ul.media-listing-grid.main-listing-grid-offset li").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = true
            ),
            hasNext = items.isNotEmpty()
        )
    }
    
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("p.titlevideospot a")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

     override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/home?search=$query"
        val document = app.get(url).document
        return document.select("ul.media-listing-grid.main-listing-grid-offset li").mapNotNull { it.toSearchResult() }
    }


    override suspend fun load(url: String): LoadResponse? {
    val document = app.get(url).document

    // Parse từ application/ld+json
    val ldJson = JSONObject(document.selectFirst("script[type=application/ld+json]")?.data() ?: return null)

    val title = ldJson.getString("name")
    val description = ldJson.optString("description", "")
    val poster = fixUrlNull(ldJson.getJSONArray("thumbnailUrl").optString(0))

    val tags = description
        .split(",")
        .map { it.trim().replace("-", "") }
        .filter { it.isNotBlank() && !StringUtil.isNumeric(it) }

    val recommendations = document.select("div#list_videos_related_videos div.video-list div.video-item")
        .mapNotNull { it.toSearchResult() }

    return newMovieLoadResponse(title, url, TvType.NSFW, url) {
        this.posterUrl = poster
        this.plot = description
        this.tags = tags
        this.recommendations = recommendations
    }
}

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val response = app.get(data)
    val html = response.text

    // Tìm nguồn video trực tiếp trong HTML gốc
    val sourcesRegex = Regex("""var\s+sources\s*=\s*(\[[\s\S]*?\]);""")
    val match = sourcesRegex.find(html) ?: return false

    val sourcesJsonText = match.groupValues[1]
        .replace(Regex("/\\*.*?\\*/"), "") // Loại bỏ comment
        .trim()

    try {
        val sourcesArray = JSONArray(sourcesJsonText)
        val extlinkList = mutableListOf<ExtractorLink>()

        for (i in 0 until sourcesArray.length()) {
            val source = sourcesArray.getJSONObject(i)
            val videoUrl = fixUrl(source.getString("src")).replace("\\/", "/")
            val qualityLabel = source.optString("desc", "Unknown")

            extlinkList.add(
                newExtractorLink(
                    source = name,
                    name = "BoyfriendTV [$qualityLabel]",
                    url = videoUrl
                ) {
                    this.referer = data
                    this.quality = when {
                        qualityLabel.contains("1080") -> 1080
                        qualityLabel.contains("720") -> 720
                        qualityLabel.contains("480") -> 480
                        qualityLabel.contains("360") -> 360
                        else -> Qualities.Unknown.value
                    }
                }
            )
        }

        extlinkList.forEach(callback)
        return extlinkList.isNotEmpty()
    } catch (e: Exception) {
        return false
    }
}
}
