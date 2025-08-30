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
    var found = false

    // 1. Try to extract from JavaScript sources array
    val scriptContent = document.select("script:containsData(sources)").firstOrNull()?.data()
    val mirrors = mutableListOf<String>()

    if (scriptContent != null) {
        val sourcesRegex = """sources\s*=\s*\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = sourcesRegex.find(scriptContent)
        match?.groups?.get(1)?.value?.let { jsonArray ->
            val urlRegex = """url":\s*"([^"]+)"""".toRegex()
            val urls = urlRegex.findAll(jsonArray)
                .map { it.groups[1]?.value }
                .filterNotNull()
                .toList()
            mirrors.addAll(urls)
        }
    }

    // 2. Fallback to iframe source
    if (mirrors.isEmpty()) {
        document.select("iframe[src]").forEach { iframe ->
            iframe.attr("src").takeIf { it.isNotBlank() }?.let { mirrors.add(it) }
        }
    }

    // 3. Additional fallback: check for alternative stream links
    if (mirrors.isEmpty()) {
        document.select("a[href*='hide.cx'], a[href*='listmirror']").forEach { link ->
            link.attr("href").takeIf { it.isNotBlank() }?.let { mirrors.add(it) }
        }
    }

    // 4. Process all found URLs
    mirrors.distinct().apmap { url ->
        val ok = loadExtractor(
            url,
            referer = data,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
        if (ok) found = true
    }

    return found
}
}