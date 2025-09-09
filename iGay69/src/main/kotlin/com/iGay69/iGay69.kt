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

    val episodes = document.select("div.single-blog-content a[href]").mapNotNull { a ->
        val href = a.attr("href").trim()
        val text = a.text().trim()
        val isPart = Regex("(?i)(part|tập)\\s*\\d+").containsMatchIn(text)
        if (href.startsWith("http") && isPart) {
            newEpisode(href) { this.name = text }
        } else null
    }.sortedBy {
        Regex("\\d+").find(it.name ?: "")?.value?.toIntOrNull() ?: Int.MAX_VALUE
    }

    return if (episodes.isNotEmpty()) {
        // TV Series
        newTvSeriesLoadResponse(title, url, TvType.NSFW, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
        }
    } else {
        // Movie
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

    // Các host hợp lệ (có extractor)
    val allowedHosts = listOf(
        "streamtape.com",
        "filemoon.to",
        "mixdrop.to",   // mxdrop.to thường redirect về mixdrop.to
        "mxdrop.to",
        "luluvid.com"   // lulustream đang dùng domain luluvid.com
    )

    fun isAllowed(url: String): Boolean {
        return try {
            val host = java.net.URI(url).host?.lowercase() ?: return false
            allowedHosts.any { host == it || host.endsWith(".$it") }
        } catch (_: Exception) {
            false
        }
    }

    // Thu thập mirrors từ tabs (ưu tiên)
    val mirrors = linkedSetOf<String>()

    document.select("div.responsive-tabs__panel a[href^=http]").forEach { a ->
        val href = a.attr("href").trim()
        if (href.isNotBlank() && isAllowed(href)) {
            mirrors.add(href)
        }
    }

    // Fallback: nếu tabs không có, thử quét toàn bộ anchor trong content
    if (mirrors.isEmpty()) {
        document.select("article a[href^=http], .single-blog-content a[href^=http]").forEach { a ->
            val href = a.attr("href").trim()
            if (href.isNotBlank() && isAllowed(href)) {
                mirrors.add(href)
            }
        }
    }

    // Fallback cuối: iframe nhưng chỉ lấy host hợp lệ để tránh dính ads
    if (mirrors.isEmpty()) {
        document.select("iframe[src^=http]").forEach { f ->
            val src = f.attr("src").trim()
            if (src.isNotBlank() && isAllowed(src)) {
                mirrors.add(src)
            }
        }
    }

    // Một số link kiểu /d/ cần mở ra thêm 1 bước để lấy link/iframe thực
    suspend fun resolveIfDoor(url: String, referer: String): String? {
        return try {
            val u = url.lowercase()
            val looksLikeDoor =
                u.contains("/d/") || u.contains("/e/") || u.contains("/f/")

            if (!looksLikeDoor) return null

            val doc = app.get(url, referer = referer).document

            // Ưu tiên iframe của host hợp lệ
            doc.select("iframe[src^=http]").firstOrNull { iframe ->
                val src = iframe.attr("src").trim()
                src.isNotBlank() && isAllowed(src)
            }?.attr("src")?.trim()?.let { return it }

            // Nếu không có iframe, thử anchor hợp lệ
            doc.select("a[href^=http]").firstOrNull { a ->
                val href = a.attr("href").trim()
                href.isNotBlank() && isAllowed(href)
            }?.attr("href")?.trim()
        } catch (_: Throwable) {
            null
        }
    }

    // Gọi extractor (chạy tuần tự để đảm bảo set found chính xác)
    for (url in mirrors) {
        // Trước tiên thử trực tiếp
        runCatching {
            val ok = loadExtractor(
                url,
                referer = data, // giữ referer là trang bài viết
                subtitleCallback = subtitleCallback,
                callback = { link -> callback(link) }
            )
            if (ok) {
                found = true
                // Không break: cho phép thu thêm mirrors khác
            } else {
                // Nếu fail, thử mở “door page” để lấy link thực rồi extract lại
                val resolved = resolveIfDoor(url, data)
                if (!resolved.isNullOrBlank()) {
                    val ok2 = loadExtractor(
                        resolved,
                        referer = url, // đổi referer sang trang door vừa mở
                        subtitleCallback = subtitleCallback,
                        callback = { link -> callback(link) }
                    )
                    if (ok2) found = true
                }
            }
        }
    }

    return found
}
}