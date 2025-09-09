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

    val title = document.selectFirst("meta[property=og:title]")?.attr("content")
        ?: document.selectFirst("h1.single-post-title")?.text()
        ?: url

    val poster = fixUrlNull(
        document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("figure.wp-block-image img")?.absUrl("src")
    )

    val description = document.selectFirst("meta[property=og:description]")?.attr("content")
        ?: document.selectFirst("div.single-blog-content")?.text()

    val recommendations = document.select("div.list-item div.video.col-2")
        .mapNotNull { it.toRecommendResult() }

    // Lấy danh sách tập (Part/Tập)
    val episodes = document.select("div.single-blog-content a[href]")
        .mapNotNull { a ->
            val href = a.attr("href").trim()
            val text = a.text().trim()
            val match = Regex("(?i)(part|tập)\\s*(\\d+)").find(text)
            val epNum = match?.groupValues?.getOrNull(2)?.toIntOrNull()
            if (href.startsWith("http") && epNum != null) {
                epNum to href
            } else null
        }
        .distinctBy { it.first }
        .sortedBy { it.first }
        .map { (epNum, href) ->
            newEpisode(href) {
                this.name = "Tập $epNum"
                this.episode = epNum
            }
        }

    return if (episodes.isNotEmpty()) {
        newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
        }
    } else {
        newMovieLoadResponse(title, url, TvType.Movie, url) {
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
    val doc = app.get(data).document
    var found = false

    // B1: lấy iframe chính
    val iframeUrl = doc.selectFirst("iframe[src]")?.absUrl("src")
    if (iframeUrl.isNullOrEmpty()) {
        Log.d("Error:", "Iframe not found")
        return false
    }

    // B2: mở iframe → tìm các nút nguồn
    val iframeDoc = app.get(iframeUrl).document
    val buttons = iframeDoc.select("a[href], button[data-href]")
    if (buttons.isEmpty()) {
        Log.d("Error:", "No source buttons found")
        return false
    }

    // B3: duyệt từng nút nguồn
    for (button in buttons) {
        val intermediateUrl = button.absUrl("href").ifBlank { button.attr("data-href") }
        if (intermediateUrl.isBlank()) continue

        try {
            // B4: mở trang trung gian → tìm link thực
            val finalDoc = app.get(intermediateUrl).document
            val finalElement = finalDoc.selectFirst("a[href], source[src]")
            val finalUrl = when (finalElement?.tagName()) {
                "a" -> finalElement.absUrl("href")
                "source" -> finalElement.absUrl("src")
                else -> null
            }

            if (!finalUrl.isNullOrEmpty()) {
                val ok = loadExtractor(
                    finalUrl,
                    referer = data,
                    subtitleCallback = subtitleCallback,
                    callback = callback
                )
                if (ok) found = true
            } else {
                Log.d("Error:", "No final link found at $intermediateUrl")
            }
        } catch (e: Exception) {
            Log.e("Error:", "Error processing link: $intermediateUrl $e")
        }
    }

    return found
}
}