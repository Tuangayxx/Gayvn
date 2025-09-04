package com.DvdGayOnline

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import com.lagradost.api.Log


class DvdGayOnline : MainAPI() {
    override var mainUrl = "https://dvdgayonline.com"
    override var name = "DvdGayOnline"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false 
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        "/genre/new/"                            to "Latest",
        "/genre/new-release/"                    to "New release",
        "/tendencia/"                            to "Trending",
        "/genre/asian/"                          to "Asian",
        "/genre/fratboys/"                       to "Fratboys",
        "/genre/group-sex/"                      to "Group",
        "/genre/gangbang/"                       to "Gangbang",
        "/genre/parody/"                         to "Parody",
        "/genre/latino/"                         to "Latino",
    )    

// ---------------- getMainPage ----------------
override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val pageUrl = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}page/$page/"
    val document = app.get(pageUrl).document

    // Chọn trực tiếp các item (article.item) nằm trong div.items (có thể có class normal hoặc không)
    val elements = document.select("div.items.normal article.item, div.items article.item")

    // dùng lambda rõ ràng để tránh lỗi suy kiểu
    val home = elements.mapNotNull { element: Element ->
        element.toSearchResult()
    }

    // detect pagination (nếu có link trang tiếp)
    val hasNext = document.select("div.pagination a").isNotEmpty()

    return newHomePageResponse(
        list = HomePageList(
            name = request.name,
            list = home,
            isHorizontalImages = false
        ),
        hasNext = hasNext
    )
}

// ---------------- toSearchResult ----------------
private fun Element.toSearchResult(): SearchResponse? {
    // lấy title: ưu tiên div.data h3 a
    val titleElement = this.selectFirst("div.data h3 a")
    val title = titleElement?.text()?.trim().orEmpty()

    // lấy href: ưu tiên poster a, fallback về data h3 a
    val posterAnchorHref = this.selectFirst("div.poster a")?.attr("href")?.takeIf { it.isNotBlank() }
    val dataHref = titleElement?.attr("href")?.takeIf { it.isNotBlank() }
    val hrefAttr = posterAnchorHref ?: dataHref ?: return null
    val href = fixUrl(hrefAttr)

    // lấy poster: thử nhiều attribute lazy / src
    val imgEl = this.selectFirst("div.poster img") ?: this.selectFirst("img")
    val posterRaw = imgEl?.attr("data-src")?.takeIf { it.isNotBlank() }
        ?: imgEl?.attr("data-lazy")?.takeIf { it.isNotBlank() }
        ?: imgEl?.attr("data-original")?.takeIf { it.isNotBlank() }
        ?: imgEl?.attr("src")?.takeIf { it.isNotBlank() }
    val posterUrl = posterRaw?.let { fixUrlNull(it) }

    return newMovieSearchResponse(title, href, TvType.NSFW) {
        this.posterUrl = posterUrl
    }
}

// ---------------- load (metadata) ----------------
override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document

    val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
        ?: document.selectFirst("h1")?.text()?.trim()
        ?: ""

    val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        ?: document.selectFirst("div.poster img")?.attr("data-src")?.takeIf { it.isNotBlank() }
        ?: document.selectFirst("div.poster img")?.attr("src")?.trim()

    val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        ?: document.selectFirst("div.text, div.entry-content, div.post-content")?.text()?.trim()
        ?: ""

    return newMovieLoadResponse(title, url, TvType.NSFW, url) {
        this.posterUrl = poster
        this.plot = description
    }
}

// ---------------- fetchPlayerUrls helper ----------------
private suspend fun fetchPlayerUrls(pageUrl: String): List<String> {
    val urls = mutableSetOf<String>()
    val doc = app.get(pageUrl).document

    // li.dooplay_player_option[data-post][data-nume]
    val options = doc.select("li.dooplay_player_option[data-post][data-nume]")
    if (options.isEmpty()) {
        // fallback: có thể có div.dooplay_player hoặc .dooplay_player_option trong nơi khác
        val fallbackOptions = doc.select("li.dooplay_player_option")
        if (fallbackOptions.isEmpty()) return urls.toList()
    }

    for (option in options) {
        val post = option.attr("data-post").trim()
        val nume = option.attr("data-nume").trim()
        if (post.isBlank() || nume.isBlank()) continue

        val apiUrl = "$mainUrl/wp-json/dooplayer/v2/?id=$post&nume=$nume"

        try {
            val respText = app.get(apiUrl).text()

            // Nếu JSON-like
            if (respText.trimStart().startsWith("{")) {
                // tìm "file":"...". Chuỗi JSON có escape \/ — thay về /
                val fileRegex = Regex("\"file\"\\s*:\\s*\"(https?:\\\\?/\\\\?/[^\"\\\\]+)\"")
                fileRegex.findAll(respText).forEach { m ->
                    val raw = m.groupValues[1].replace("\\/", "/")
                    urls.add(fixUrl(raw))
                }

                // fallback: tìm tất cả URL mp4/m3u8 trong chuỗi JSON response
                val urlRegex = Regex("(https?:\\\\?/\\\\?/[^\"\\\\\\s]+\\.(?:m3u8|mp4))")
                urlRegex.findAll(respText).forEach { m ->
                    val raw = m.groupValues[1].replace("\\/", "/")
                    urls.add(fixUrl(raw))
                }
            } else {
                // parse HTML fragment
                val playerDoc = Jsoup.parse(respText)

                playerDoc.select("iframe[src]").mapNotNull { it.attr("src").takeIf { s -> s.isNotBlank() } }
                    .forEach { urls.add(fixUrl(it)) }

                playerDoc.select("video source[src], source[src]").mapNotNull { it.attr("src").takeIf { s -> s.isNotBlank() } }
                    .forEach { urls.add(fixUrl(it)) }

                // inline script chứa file: "..."
                val scriptFileRegex = Regex("\"file\"\\s*:\\s*\"(https?:\\\\?/\\\\?/[^\"\\\\]+)\"")
                scriptFileRegex.findAll(respText).forEach { m ->
                    val raw = m.groupValues[1].replace("\\/", "/")
                    urls.add(fixUrl(raw))
                }
            }
        } catch (e: Exception) {
            // optional: log or print error
            // println("fetchPlayerUrls error for $apiUrl: ${e.message}")
        }
    }

    return urls.filter { it.isNotBlank() }
}

// ---------------- loadLinks (ví dụ tích hợp helper) ----------------
override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    // data là URL trang phim
    val videoUrls = fetchPlayerUrls(data).toMutableList()

    // fallback: nếu không có link từ API, kiểm tra iframe trực tiếp trên trang
    if (videoUrls.isEmpty()) {
        val doc = app.get(data).document
        doc.select("iframe[src]").mapNotNull { it.attr("src").takeIf { s -> s.isNotBlank() } }
            .forEach { videoUrls.add(fixUrl(it)) }
    }

    if (videoUrls.isEmpty()) return false

    for ((index, videoUrl) in videoUrls.withIndex()) {
        val srcName = "Source ${index + 1}"

        // CÁCH A: DÙNG constructor cũ (deprecated) nếu bạn chưa có newExtractorLink:
        // callback(ExtractorLink("dvd", srcName, videoUrl, data))

        // CÁCH B: nếu project có helper newExtractorLink(...) (kết quả tốt hơn)
        // Ví dụ giả (thay bằng signature thật nếu khác):
        // callback(newExtractorLink(srcName, videoUrl, referer = data))

        // Tạm dùng constructor cũ để chắc chắn compile (chỉ cảnh báo deprecated)
        callback(ExtractorLink("dvd", srcName, videoUrl, data))
    }

    return true
}
}
