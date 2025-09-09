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

    // Tạo danh sách tập phim (chỉ gán tên và data, không gán link phát)
    val episodes = document.select("div.single-blog-content a[href]").mapNotNull { a ->
        val href = a.attr("href").trim()
        val text = a.text().trim()
        val match = Regex("(?i)(part|tập)\\s*(\\d+)").find(text)
        val partNumber = match?.groupValues?.getOrNull(2)?.toIntOrNull()

        if (href.startsWith("http") && partNumber != null) {
            newEpisode(href) {
                this.name = "Tập $partNumber"
            }
        } else null
    }.sortedBy {
        Regex("\\d+").find(it.name ?: "")?.value?.toIntOrNull() ?: Int.MAX_VALUE
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
    val document = app.get(data).document
    var found = false

    val allowedHosts = listOf(
        "mixdrop.co", "mixdrop.to", "streamtape.com", "filemoon.sx", "filemoon.to",
        "dood.watch", "dood.so", "voe.sx", "voe.to", "vidhide.com", "upstream.to"
    )

    val mirrors = linkedSetOf<String>()

    // Quét các link trong tab nguồn phim
    document.select("div.responsive-tabs__panel a[href]").forEach { a ->
        val href = a.attr("href").trim()
        val host = runCatching { java.net.URI(href).host?.lowercase() }.getOrNull()
        if (href.startsWith("http") && host != null && allowedHosts.any { host.contains(it) }) {
            mirrors.add(href)
        }
    }

    // Fallback: iframe nếu không có tab
    if (mirrors.isEmpty()) {
        document.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src").trim()
            val host = runCatching { java.net.URI(src).host?.lowercase() }.getOrNull()
            if (src.startsWith("http") && host != null && allowedHosts.any { host.contains(it) }) {
                mirrors.add(src)
            }
        }
    }

    // Gọi extractor cho từng link
    for (url in mirrors) {
        val ok = loadExtractor(
            url,
            referer = data,
            subtitleCallback = subtitleCallback,
            callback = { link -> callback(link) }
        )
        if (ok) found = true
    }

    return found
}
}