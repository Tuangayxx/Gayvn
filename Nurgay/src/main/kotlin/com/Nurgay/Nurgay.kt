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
    Log.d("Nurgay", "=== LOAD LINKS for: $data ===")
    Log.d("Nurgay", "Document title: ${document.selectFirst("title")?.text() ?: "no title"}")
    val htmlPrefix = document.html().take(60000)
    Log.d("Nurgay", "Page HTML (prefix 60KB): ${htmlPrefix.replace("\n","\\n").take(8000)}")

    var found = false

    // Mirrors
    val mirrors = document.select("ul#mirrorMenu a.mirror-opt, a.dropdown-item.mirror-opt")
        .mapNotNull { it.attr("data-url").takeIf { u -> u.isNotBlank() && u != "#" } }
        .toMutableSet()
    Log.d("Nurgay", "Mirrors found from data-url: ${mirrors.joinToString()}")

    // Fallback iframe
    if (mirrors.isEmpty()) {
        val iframeSrc = document.selectFirst("iframe[src]")?.attr("src")
        Log.d("Nurgay", "No mirrors; iframe src = $iframeSrc")
        iframeSrc?.let { mirrors.add(it) }
    }

    // Wrap the callback to log every link returned by the extractor
    mirrors.toList().amap { url ->
        Log.d("Nurgay", "Trying loadExtractor for: $url (referer=$data)")
        val ok = loadExtractor(
            url,
            referer = data,
            subtitleCallback = subtitleCallback
        ) { link ->
            // This runs when extractor yields a link
            Log.d("Nurgay", "EXTRACTOR CALLBACK -> url=${link.url} quality=${link.quality} isVideo=${link.isVideo} name=${link.name}")
            // forward to main callback so CloudStream receives it
            callback(link)
        }
        Log.d("Nurgay", "loadExtractor returned $ok for $url")
        if (ok) found = true
    }

    Log.d("Nurgay", "=== finished loadLinks; found=$found ===")
    return found
}
}