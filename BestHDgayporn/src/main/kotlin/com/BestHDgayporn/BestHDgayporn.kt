package com.BestHDgayporn

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.io.IOException

class BestHDgayporn : MainAPI() {
    // Main provider information
    override var mainUrl = "https://besthdgayporn.com"
    override var name = "BestHDgayporn"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    // Main page categories
    override val mainPage = mainPageOf(
        "" to "Latest",
        "/video-tag/onlyfans" to "Onlyfans",
        "/video-tag/men-com" to "MEN.com",
        "/video-tag/corbin-fisher" to "Corbin Fisher",
        "/video-tag/raw-fuck-club" to "Raw fuck club",
        "/video-tag/randy-blue" to "Randy Blue",
        "/video-tag/sean-cody" to "Sean Cody",
        "/video-tag/falcon-studios" to "Falcon Studio",
        "/video-tag/voyr" to "Voyr",
        "/video-tag/next-door-studios" to "Next Door Studios",
        "/video-tag/noir-male" to "Noir Male",
        "/video-tag/asg-max" to "ASG Max",
    )

    // Fetch data for the main page
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.isBlank()) {
            "$mainUrl/page/$page/"
        } else {
            "$mainUrl${request.data}/page/$page/"
        }

        val document = app.get(url).document
        // Use the correct selector to find video items
        val items = document.select("div.aiovg-item-video")
        val videos = items.mapNotNull { it.toSearchResult() }

        // Check for a next page
        val hasNext = document.selectFirst("a.next.page-numbers") != null

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = videos,
                isHorizontalImages = true
            ),
            hasNext = hasNext
        )
    }

    // Convert an HTML element to a SearchResponse
    private fun Element.toSearchResult(): SearchResponse? {
        // Get the link and title from the anchor element in the caption
        val linkElement = this.selectFirst("div.aiovg-caption a.aiovg-link-title") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        val title = linkElement.text().trim()

        // Get the poster image, prioritizing "data-src" for lazy-loading
        val imgElement = this.selectFirst("img.aiovg-responsive-element")
        val posterUrl = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: imgElement?.attr("src")

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    // Search for videos
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        val items = document.select("div.aiovg-item-video")
        return items.mapNotNull { it.toSearchResult() }
    }

  override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document

    // Lấy tiêu đề, poster, mô tả như trước
    val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: ""
    val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
    val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

    // Trả về một "episode" duy nhất, dùng chính URL trang làm data
    val episodes = listOf(Episode(data = url, name = "Play"))

    return newMovieLoadResponse(title, url, TvType.NSFW, episodes) {
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
    // 'data' bây giờ chính là URL trang chi tiết
    val document = app.get(data).document

    // Chọn thẻ <video> (class vjs-tech) hoặc trực tiếp <source> bên trong
    document.select("video#player_html5_api, video").forEach { video ->
        // Ưu tiên lấy src của chính <video>, nếu không có thì lấy từ <source>
        val src = video.attr("src").ifBlank {
            video.selectFirst("source")?.attr("src").orEmpty()
        }
        if (src.isNotBlank()) {
            loadExtractor(src, subtitleCallback, callback)
        }
    }
    return true
}
}
