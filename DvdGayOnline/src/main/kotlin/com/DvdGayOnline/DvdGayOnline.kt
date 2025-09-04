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
    
override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val pageUrl = if (page == 1)
        "$mainUrl${request.data}"
    else
        "$mainUrl${request.data}page/$page/"

    val document = app.get(pageUrl).document

    // Chọn trực tiếp các article.item bên trong div.items (kể cả khi class "normal" có/không)
    val home = document
        .select("div.items.normal article.item, div.items article.item")
        .mapNotNull { it.toSearchResult() }

    // Detect pagination: nếu có link <div class="pagination"> chứa <a> thì có trang tiếp
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

    val videoUrls = mutableSetOf<String>()

    // Lấy URL từ iframe player chính
    val iframeSrc = document.selectFirst("iframe.metaframe")?.attr("src")
    iframeSrc?.let { videoUrls.add(it) }

    // Nếu trang có thêm link download
    val button = document.selectFirst("a.download[href]")?.attr("href")
    button?.let { videoUrls.add(it) }

    // Xử lý tất cả URL đã thu thập
    videoUrls.toList().amap { url ->
        val ok = loadExtractor(
            url,
            referer = data,
            subtitleCallback = subtitleCallback
        ) { link -> callback(link) }
        if (ok) found = true
    }

    return found
}

}
