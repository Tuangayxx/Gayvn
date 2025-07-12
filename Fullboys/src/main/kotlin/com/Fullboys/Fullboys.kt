package com.Fullboys

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.json.JSONObject

class Fullboys : MainAPI() {
    private val globalTvType = TvType.NSFW
    override var mainUrl = "https://fullboys.com"
    override var name = "Fullboys"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "/topic/video/muscle"      to "Muscle",
        "/topic/video/korean"      to "Korean",
        "/topic/video/japanese"    to "Japanese",
        "/topic/video/taiwanese"   to "Taiwanese",
        "/topic/video/viet-nam"    to "Vietnamese",
        "/topic/video/philippines" to "Philippines",
        "/topic/video/thailand"    to "Thái Lan",
        "/topic/video/group"       to "Tập thể",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}?page=$page"
        val document = app.get(pageUrl).document

        val items = document.select("article.movie-item").mapNotNull { toSearchItem(it) }

        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = true
            ),
            hasNext = items.isNotEmpty()
        )
    }

    private fun toSearchItem(element: Element): SearchResponse? {
        val aTag = element.selectFirst("a") ?: return null
        val url = fixUrl(aTag.attr("href"))
        val name = aTag.selectFirst("h2.title")?.text() ?: return null

        val image = aTag.selectFirst("img")?.let {
            it.attr("data-cfsrc").takeIf { src -> src.isNotBlank() } ?: it.attr("src")
        } ?: return null

         return MovieSearchResponse(
            name = name,
            url = url,
            apiName = this@Fullboys.name,
            type = TvType.NSFW,
            posterUrl = image
        )
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/home?search=$query"
        val document = app.get(url).document
        return document.select("article.movie-item").mapNotNull { toSearchItem(it) }
    }


    override suspend fun load(url: String): LoadResponse? {
    val doc = app.get(url).document

    // Lấy tên video
    val name = doc.selectFirst("h1.title-detail")?.text()?.trim() ?: return null

    // Lấy link video từ iframe
    val iframeSrc = doc.selectFirst("iframe#ifvideo")?.attr("src")
    val videoUrl = iframeSrc?.let {
        Regex("[?&]video=([^&]+)").find(it)?.groupValues?.getOrNull(1)
    }?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    if (videoUrl.isNullOrBlank()) return null

    // Lấy poster từ meta hoặc param poster trên iframe
    val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        ?: iframeSrc?.let {
            Regex("[?&]poster=([^&]+)").find(it)?.groupValues?.getOrNull(1)
        }?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    // Mô tả
    val description = doc.selectFirst("meta[name=description]")?.attr("content").orEmpty()

    // Tags
    val tags = doc.select("div.video-tags a").map { it.text().trim() }

    // Gợi ý: lấy ảnh preview (nếu muốn)
    val previews = doc.select("img.preview-image").map { it.attr("src") }

    // Gợi ý: lấy video liên quan
    val recommendations = doc.select("article.movie-item").mapNotNull { el ->
        val aTag = el.selectFirst("a") ?: return@mapNotNull null
        val recUrl = fixUrl(aTag.attr("href"))
        val recName = aTag.attr("title") ?: aTag.selectFirst("h2.title")?.text() ?: return@mapNotNull null
        val recPoster = aTag.selectFirst("img")?.attr("src")
            MovieSearchResponse( 
            name = recName,
            url = recUrl,
            apiName = this@Fullboys.name,
            type = TvType.NSFW,
            posterUrl = recPoster
        )
    }

    return MovieLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = TvType.NSFW,
        dataUrl = videoUrl,
        posterUrl = poster,
        plot = description,
        tags = tags
    ).apply {
        this.recommendations = recommendations
        // Nếu muốn, có thể thêm previews vào 1 trường custom nếu app hỗ trợ
    }
}
    
    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    // data là url gốc trang video

    // Lấy lại HTML trang video (nên để tránh miss các link)
    val doc = app.get(data).document

    // Lấy link từ các button server
    val serverLinks = doc.select(".box-server button[onclick]").mapNotNull { btn ->
        // Tìm link trong onclick
        val onclick = btn.attr("onclick")
        val regex = Regex("['\"](https?://[^'\"]+\\.mp4)['\"]")
        val match = regex.find(onclick)
        val url = match?.groupValues?.getOrNull(1)
        val label = btn.text().trim()
        // Có thể đặt chất lượng theo server hoặc tự xác định
        if (url != null) Pair(url, label) else null
    }

    // Nếu không tìm thấy, fallback về link cũ
    if (serverLinks.isEmpty()) {
        callback(
            newExtractorLink(
                source = name,
                name = "Fullboys Stream",
                url = data
            )
        )
        return true
    }

    // Trả về từng chất lượng/server
    serverLinks.forEach { (url, label) ->
        callback(
            newExtractorLink(
                source = name,
                name = label, // VD: "Server 1", "Server 2"
                url = url
            )
        )
    }
    return true
    }
}
