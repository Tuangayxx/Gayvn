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
    val found = linkedSetOf<String>()

    // thu thập mọi candidate URL như trước (meta, iframe, data-url, a[href])
    document.selectFirst("meta[itemprop=embedURL]")?.attr("content")?.takeIf { it.isNotBlank() }?.let { found.add(it) }
    document.select("div.responsive-player iframe[src]").forEach { it.attr("src").takeIf { s -> s.isNotBlank() }?.let(found::add) }
    document.select("iframe[src]").forEach { it.attr("src").takeIf { s -> s.isNotBlank() }?.let(found::add) }
    document.select("[data-url]").forEach { it.attr("data-url").takeIf { s -> s.isNotBlank() }?.let(found::add) }
    document.select("a[href]").forEach { it.attr("href").takeIf { s -> s.isNotBlank() }?.let(found::add) }

    // chuẩn hoá và lọc chỉ lấy host cho phép
    val allowedHosts = listOf("bigwarp.io", "d-s.io", "vinovo.to", "voe.sx")
    val candidates = found.mapNotNull { raw ->
        val url = if (raw.startsWith("//")) "https:$raw" else if (raw.startsWith("/")) fixUrl(raw) else if (!raw.startsWith("http")) fixUrl(raw) else raw
        try {
            val host = java.net.URI(url).host?.lowercase()?.removePrefix("www.")
            if (host != null && allowedHosts.any { host.endsWith(it) }) url else null
        } catch (e: Exception) {
            // nếu parse lỗi, fallback kiểm tra chuỗi
            if (allowedHosts.any { raw.contains(it, ignoreCase = true) }) url else null
        }
    }.toSet() // loại trùng

    com.lagradost.api.Log.d("Nurgay", "All found URLs: $found")
    com.lagradost.api.Log.d("Nurgay", "Filtered allowed URLs: $candidates")

    // gọi extractor chỉ cho các link đã lọc
    candidates.forEach { url ->
        loadExtractor(url, subtitleCallback, callback)
    }

    return candidates.isNotEmpty()
}

}