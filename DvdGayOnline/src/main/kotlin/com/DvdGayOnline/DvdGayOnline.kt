package com.DvdGayOnline

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val pageUrl = if (page == 1)
        "$mainUrl${request.data}"
    else
        "$mainUrl${request.data}page/$page/"

    val document = app.get(pageUrl).document
    val home = document.select("div.items.normal article.item, div.items article.item").mapNotNull { it.toSearchResult() }

    return newHomePageResponse(
        list = HomePageList(
            name = request.name,
            list = home,
            isHorizontalImages = false
        ),
        hasNext = true
  )
}
    
private fun Element.toSearchResult(): SearchResponse? {
    // ưu tiên lấy title từ data h3 a
    val titleEl = this.selectFirst("div.data h3 a") ?: return null
    val title = titleEl.text().trim()
    // lấy href: ưu tiên poster a, fallback về data h3 a
    val posterAnchor = this.selectFirst("div.poster a")?.attr("href")?.takeIf { it.isNotBlank() }
    val hrefAttr = posterAnchor ?: titleEl.attr("href")?.takeIf { it.isNotBlank() } ?: return null
    val href = fixUrl(hrefAttr)

    // lấy poster: ưu tiên data-src (lazy), fallback src
    val imgEl = this.selectFirst("div.poster img") ?: this.selectFirst("img")
    val posterRaw = imgEl?.attr("data-src")?.takeIf { it.isNotBlank() }
        ?: imgEl?.attr("src")?.takeIf { it.isNotBlank() }
    val posterUrl = posterRaw?.let { fixUrlNull(it) }

    return newMovieSearchResponse(title, href, TvType.NSFW) {
        this.posterUrl = posterUrl
    }
}

override suspend fun search(query: String): List<SearchResponse> {
    val searchResponse = mutableListOf<SearchResponse>()

    for (i in 1..7) {
        val document = app.get("$mainUrl/page/$i/?s=$query").document
        
        val results = document.select("div.items.normal").mapNotNull { it.toSearchResult() }
        if (results.isEmpty()) break
        searchResponse.addAll(results)
    }

    return searchResponse
}

   

    override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document

    val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
        ?: document.selectFirst("h1")?.text()?.trim()
        ?: ""

    val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        ?: document.selectFirst("div.poster img")?.attr("data-src")?.takeIf { it.isNotBlank() }
        ?: document.selectFirst("div.poster img")?.attr("src")?.trim()

    val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        ?: document.selectFirst("div.text")?.text()?.trim()
        ?: ""

    return newMovieLoadResponse(title, url, TvType.NSFW, url) {
        this.posterUrl = poster
        this.plot = description
    }
}
private suspend fun fetchPlayerUrls(pageUrl: String): List<String> {
    val urls = mutableSetOf<String>()
    val doc = app.get(pageUrl).document

    // chọn các option player (nếu có)
    val options = doc.select("li.dooplay_player_option[data-post][data-nume]")
    if (options.isEmpty()) return urls.toList()

    for (opt in options) {
        val post = opt.attr("data-post").trim()
        val nume = opt.attr("data-nume").trim()
        if (post.isBlank() || nume.isBlank()) continue

        val apiUrl = "$mainUrl/wp-json/dooplayer/v2/?id=$post&nume=$nume"
        try {
            val respText = app.get(apiUrl).text()

            // Nếu JSON-like -> tìm "file":"..." hoặc các URL trong chuỗi
            if (respText.trimStart().startsWith("{")) {
                // tìm file: "..." (cũng xử lý \/ escape)
                Regex("\"file\"\\s*:\\s*\"(https?:\\\\?/\\\\?/[^\"\\\\]+)\"")
                    .findAll(respText)
                    .forEach { m -> urls.add(fixUrl(m.groupValues[1].replace("\\/", "/"))) }

                // fallback: tìm tất cả chuỗi https://... có đuôi mp4/m3u8
                Regex("(https?:\\\\?/\\\\?/[^\"\\\\\\s]+\\.(?:m3u8|mp4|json))")
                    .findAll(respText)
                    .forEach { m -> urls.add(fixUrl(m.groupValues[1].replace("\\/", "/"))) }
            } else {
                // parse như HTML fragment
                val playerDoc = org.jsoup.Jsoup.parse(respText)

                playerDoc.select("iframe[src]").mapNotNull { it.attr("src").takeIf { s -> s.isNotBlank() } }
                    .forEach { urls.add(fixUrl(it)) }

                playerDoc.select("video source[src], source[src]").mapNotNull { it.attr("src").takeIf { s -> s.isNotBlank() } }
                    .forEach { urls.add(fixUrl(it)) }

                // script inline chứa file: "..."
                Regex("\"file\"\\s*:\\s*\"(https?:\\\\?/\\\\?/[^\"\\\\]+)\"")
                    .findAll(respText)
                    .forEach { m -> urls.add(fixUrl(m.groupValues[1].replace("\\/", "/"))) }
            }
        } catch (e: Exception) {
            // log nếu bạn muốn, nhưng tiếp tục với option tiếp theo
        }
    }

    return urls.filter { it.isNotBlank() }
}


    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    // data là url trang phim
    val videoUrls = fetchPlayerUrls(data).toMutableList()

    // fallback: nếu không có, thử lấy iframe trực tiếp từ trang
    if (videoUrls.isEmpty()) {
        val doc = app.get(data).document
        doc.select("iframe[src]").mapNotNull { it.attr("src").takeIf { s -> s.isNotBlank() } }
            .forEach { videoUrls.add(fixUrl(it)) }
    }

    if (videoUrls.isEmpty()) return false

    for ((index, videoUrl) in videoUrls.withIndex()) {
        // Tạo ExtractorLink: sửa cho đúng constructor trong repo của bạn
        // Ví dụ giả: ExtractorLink(name, url, referer)
        val name = "Source ${index + 1}"
        // --- CHỖ NÀY CẦN BẠN THAY THEO CONSTRUCTOR THẬT ---
        // Ex: callback(ExtractorLink(name, videoUrl, videoUrl))
        callback(ExtractorLink(name, videoUrl, videoUrl))
    }

    return true
}
}
