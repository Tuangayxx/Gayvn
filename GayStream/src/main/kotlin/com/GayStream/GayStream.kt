package com.GayStream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.nodes.Element
import java.io.IOException
import com.lagradost.api.Log


class GayStream : MainAPI() {
    override var mainUrl = "https://gaystream.pw"
    override var name = "GayStream"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        ""                                      to "Latest",
        "/video/category/4k"                    to "4K",
        "/video/category/anal"                  to "Anal",
        "/video/category/asian"                 to "Asian",
        "/video/category/dp"                    to "DP",
        "/video/category/group"                 to "Group",
        "/video/category/homemade"              to "Homemade",
        "/video/category/hunk"                  to "Hunk",
        "/video/category/latino"                to "Latino",
        "/video/channel/betabetapi"             to "Beta Beta Pi",
        "/video/channel/caninolatino"           to "Canino Latino",
    )    

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val pageUrl = if (page == 1) 
        "$mainUrl${request.data}" 
    else 
        "$mainUrl{request.data}/page/$page$" // ✅ Sửa pagination

    val document = app.get(pageUrl).document
    val home = document.select("div.grid-item").mapNotNull { it.toSearchResult() }

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
    val title = this.select("h3.item-title").text() // ✅ Sửa lấy text
    val href = fixUrl(this.select("a.item-wrap").attr("href"))
    val posterUrl = fixUrlNull(this.select("img.item-img").attr("src"))
    
    return newMovieSearchResponse(title, href, TvType.NSFW) {
        this.posterUrl = posterUrl
    }
}

override suspend fun search(query: String): List<SearchResponse> {
    val searchResponse = mutableListOf<SearchResponse>()

    for (i in 1..7) {
        // ✅ Sửa URL search: thêm `&page=i`
        val document = app.get("$mainUrl/?s=$query&page=$i").document
        val results = document.select("div.grid-item").mapNotNull { it.toSearchResult() }

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

    // Lấy URL từ các button trong tabs-wrap
    val videoUrls = document.select("div.tabs-wrap button[onclick]")
        .mapNotNull { it.attr("onclick").takeIf { u -> u.isNotBlank() && u != "#" } }
        .mapNotNull { onclick ->
            // Tách URL từ onclick="document.getElementById('ifr').src='URL'"
            Regex("src=(?:&quot;|\"|')(.*?)(?:&quot;|\"|')").find(onclick)?.groupValues?.get(1)
        }
        .toMutableSet()

    // Fallback iframe (trường hợp không có button)
    if (videoUrls.isEmpty()) {
        val iframeSrc = document.selectFirst("iframe#ifr")?.attr("src")
        iframeSrc?.let { videoUrls.add(it) }
    }

    // Thu thập URL từ nút download
    val button = document.selectFirst("a.video-download[href]")?.attr("href")
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