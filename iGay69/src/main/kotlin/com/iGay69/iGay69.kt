package com.iGay69

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.nodes.Element
import java.io.IOException
import com.lagradost.api.Log


class iGay69 : MainAPI() {
    override var mainUrl = "https://igay69.com"
    override var name = "iGay69"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        ""                         to "Latest",
        "category/porn/gaydar-porn"                    to "Gaydar",
        "category/leak/march-cmu"                                to "March CMU",
        "araw-2025"                             to "Araw",
    )    

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val pageUrl = if (page == 1) 
        "$mainUrl/${request.data}" 
    else 
        "$mainUrl/${request.data}/page/$page"

    val document = app.get(pageUrl).document
    val home = document.select("article.blog-entry").mapNotNull { it.toSearchResult() }

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
    val title = this.select("h2.wpex-card-title a").text()
    val href = fixUrl(this.select("h2.wpex-card-title a").attr("href"))
    val posterUrl = fixUrlNull(this.select("div.wpex-card-thumbnail img").attr("src"))

    return newMovieSearchResponse(title, href, TvType.NSFW) {
        this.posterUrl = posterUrl
    }
}

private fun Element.toRecommendResult(): SearchResponse? {
    val title = this.select("h2.wpex-card-title a").text()
    val href = fixUrl(this.select("h2.wpex-card-title a").attr("href"))
    val posterUrl = fixUrlNull(this.select("div.wpex-card-thumbnail img").attr("src"))
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

override suspend fun search(query: String): List<SearchResponse> {
    val searchResponse = mutableListOf<SearchResponse>()

    for (i in 1..7) {
        val document = app.get("$mainUrl/?s=$query&page=$i").document
        val results = document.select("article.blog-entry").mapNotNull { it.toSearchResult() }

        if (results.isEmpty()) break
        searchResponse.addAll(results)
    }

    return searchResponse
}
   
override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document

    val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
        ?: document.selectFirst("h1.single-post-title")?.text()?.trim()
        ?: url

    val poster = fixUrlNull(
        document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: listOf("data-src", "data-lazy-src", "src")
                .mapNotNull { attr -> document.selectFirst("figure.wp-block-image img")?.absUrl(attr) }
                .firstOrNull { it.isNotBlank() }
    )

    val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        ?: document.selectFirst("div.single-blog-content")?.text()?.trim()

    val recommendations = document.select("div.list-item div.video.col-2")
        .mapNotNull { it.toRecommendResult() }

    // Lấy danh sách tập, loại trùng theo số tập
    val episodes = document.select("div.single-blog-content a[href]")
        .mapNotNull { a ->
            val href = a.attr("href").trim()
            val text = a.text().trim()
            val match = Regex("(?i)(part|tập)\\s*(\\d+)").find(text)
            val partNumber = match?.groupValues?.getOrNull(2)?.toIntOrNull()

            if (href.startsWith("http") && partNumber != null) {
                partNumber to href
            } else null
        }
        .distinctBy { it.first } // chỉ giữ mỗi số tập 1 lần
        .sortedBy { it.first }
        .map { (partNumber, href) ->
            newEpisode(href) {
                this.name = "Tập $partNumber"
            }
        }

    return if (episodes.isNotEmpty()) {
        newTvSeriesLoadResponse(title, url, TvType.NSFW, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
        }
    } else {
        newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
        }
    }
}

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    var found = false
    val visited = mutableSetOf<String>()

    // Danh sách domain server cần lấy
    val serverDomains = listOf(
        "mxdrop.to", "mixdrop.to",
        "streamtape.com",
        "filemoon.to",
        "dood", "doodstream.com", // doodstream, dood.to...
        "vidcloud"
    )

    suspend fun processLink(link: String, referer: String) {
        if (visited.add(link)) {
            val ok = loadExtractor(link, referer = referer, subtitleCallback = subtitleCallback, callback = callback)
            if (ok) found = true
        }
    }

    suspend fun extractFromPage(pageUrl: String, referer: String) {
        val doc = runCatching { app.get(pageUrl, referer = referer).document }.getOrNull() ?: return

        // 1. Quét iframe và a[href]
        doc.select("iframe[src], a[href]").forEach { el ->
            val link = el.absUrl("src").ifBlank { el.absUrl("href") }.trim()
            if (link.startsWith("http") && serverDomains.any { link.contains(it, ignoreCase = true) }) {
                processLink(link, pageUrl)
            }
        }

        // 2. Quét link trong toàn bộ HTML/text (bắt cả markdown)
        val regex = Regex("""https?://[^\s"')<>]+""")
        regex.findAll(doc.html()).forEach { match ->
            val link = match.value
            if (serverDomains.any { link.contains(it, ignoreCase = true) }) {
                processLink(link, pageUrl)
            }
        }
    }

    // Mở trang chính
    val doc = runCatching { app.get(data).document }.getOrNull() ?: return false
    val iframeUrl = doc.selectFirst("iframe[src]")?.absUrl("src")

    if (!iframeUrl.isNullOrEmpty()) {
        val iframeDoc = runCatching { app.get(iframeUrl, referer = data).document }.getOrNull()
        val intermediateLinks = iframeDoc?.select("a[href]")?.mapNotNull { it.absUrl("href").takeIf { href -> href.startsWith("http") } } ?: emptyList()

        if (intermediateLinks.isNotEmpty()) {
            for (link in intermediateLinks) extractFromPage(link, referer = iframeUrl)
        } else {
            extractFromPage(iframeUrl, referer = data)
        }
    } else {
        extractFromPage(data, referer = data)
    }

    return found
}
}