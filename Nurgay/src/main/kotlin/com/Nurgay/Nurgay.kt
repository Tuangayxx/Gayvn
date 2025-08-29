package com.Nurgay

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.nodes.Element
import java.io.IOException
import com.lagradost.api.Log


class Nurgay : MainAPI() {
    override var mainUrl = "https://nurgay.to"
    override var name = "Nurgay"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        "/?filter=latest"                         to "Latest",
        "/?filter=most-viewed"                    to "Most Viewed",
        "/asiaten"                                to "Asian",
        "/gruppensex"                             to "Group Sex",
        "/bisex"                                  to "Bisexual",
        "/hunks"                                  to "Hunks",
        "/latino"                                 to "Latino",
        "/muskeln"                                to "Muscle",
        "/bareback"                               to "Bareback",
    )    

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val pageUrl = if (page == 1) 
        "$mainUrl${request.data}" 
    else 
        "$mainUrl/page/$page${request.data}" // ✅ Sửa pagination

    val document = app.get(pageUrl).document
    val home = document.select("article.loop-video").mapNotNull { it.toSearchResult() }

    return newHomePageResponse(
        list = HomePageList(
            name = request.name,
            list = home,
            isHorizontalImages = false
        ),
        hasNext = true
    )
}

private fun Element.toSearchResult(): SearchResponse {
    val title = this.select("header.entry-header span").text() // ✅ Sửa lấy text
    val href = fixUrl(this.select("a").attr("href"))
    val posterUrl = fixUrlNull(this.select("img").attr("data-src"))
    
    return newMovieSearchResponse(title, href, TvType.NSFW) {
        this.posterUrl = posterUrl
    }
}

override suspend fun search(query: String): List<SearchResponse> {
    val searchResponse = mutableListOf<SearchResponse>()

    for (i in 1..7) {
        // ✅ Sửa URL search: thêm `&page=i`
        val document = app.get("$mainUrl/?s=$query&page=$i").document
        val results = document.select("article.loop-video").mapNotNull { it.toSearchResult() }

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
    val videoUrls = linkedSetOf<String>()

    // 1) meta embedURL
    document.selectFirst("meta[itemprop=embedURL]")?.attr("content")?.takeIf { it.isNotBlank() }?.let {
        videoUrls.add(if (it.startsWith("http")) it else fixUrl(it))
    }

    // 2) iframe trong responsive-player
    document.select("div.responsive-player iframe[src]").forEach { iframe ->
        iframe.attr("src").takeIf { it.isNotBlank() }?.let { videoUrls.add(if (it.startsWith("http")) it else fixUrl(it)) }
    }

    // 3) tất cả iframe có src (dự phòng)
    document.select("iframe[src]").forEach { iframe ->
        iframe.attr("src").takeIf { it.isNotBlank() }?.let { videoUrls.add(if (it.startsWith("http")) it else fixUrl(it)) }
    }

    // 4) các thẻ có data-url (ví dụ dropdown / JS-inserted links)
    document.select("[data-url]").forEach {
        it.attr("data-url").takeIf { v -> v.isNotBlank() }?.let { v ->
            videoUrls.add(if (v.startsWith("http")) v else fixUrl(v))
        }
    }

    // 5) anchors tới host phổ biến (mở rộng tuỳ bạn)
    val hosts = listOf("listmirror","doodstream","bigwarp","vide0","d-s.io","pixeldrain","hide.cx","ddownload","rapidgator","pixeldrain.com")
    document.select("a[href]").forEach { a ->
        val href = a.attr("href")
        if (href.isNotBlank() && hosts.any { host -> href.contains(host, ignoreCase = true) }) {
            videoUrls.add(if (href.startsWith("http")) href else fixUrl(href))
        }
    }

    // Debug log (bật nếu cần)
    com.lagradost.api.Log.d("Nurgay", "Found video URLs: $videoUrls")

    // xử lý
    videoUrls.forEach { url ->
        loadExtractor(url, subtitleCallback, callback)
    }

    return videoUrls.isNotEmpty()
}
}