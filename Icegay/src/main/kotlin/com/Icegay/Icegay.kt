package com.Icegay

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Element

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


    fun getQualityFromName(label: String?): Int {
    return when {
        label == null -> Qualities.Unknown.value
        label.contains("1080", true) || label.equals("Hd", true) -> Qualities.P1080.value
        label.contains("720", true) || label.equals("High", true) -> Qualities.P720.value
        label.contains("480", true) || label.equals("Low", true) -> Qualities.P480.value
        label.contains("360", true) -> Qualities.P360.value
        else -> Qualities.Unknown.value
    }
}

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val document = app.get(data).document

    // Lấy embedUrl từ JSON-LD
    val ldJson = JSONObject(document.selectFirst("script[type=application/ld+json]")?.data() ?: return false)
    val embedUrl = fixUrl(ldJson.optString("embedUrl") ?: return false)

    // Truy cập embed page
    val embedDoc = app.get(embedUrl, referer = data).document

    // Lấy script chứa setVideoUrlX()
    val script = embedDoc.selectFirst("script:containsData(setVideoUrl)")?.data() ?: return false

    // Regex bắt các dòng video
    val regex = Regex("""setVideoUrl(\w+)\(['"](.+?)['"]\)""")
    val matches = regex.findAll(script)

    // Duyệt từng video chất lượng
    matches.forEach { match ->
        val label = match.groupValues[1] // Low, High, Hd, etc.
        val url = match.groupValues[2]

        callback(
            newExtractorLink(
                source = name,
                name = "BoyfriendTV [$label]",
                url = fixUrl(url),
                type = INFER_TYPE
            ) {
                this.referer = embedUrl
                this.quality = getQualityFromName(label)
                this.isM3u8 = url.endsWith(".m3u8")
            }
        )
    }

    return true
}

}
