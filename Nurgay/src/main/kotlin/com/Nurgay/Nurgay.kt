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

    // thu thập các url duy nhất để tránh gọi lặp
    val urls = linkedSetOf<String>()

    // normalize an url: handle protocol-relative, relative paths, and invalid cases
    fun normalize(raw: String): String {
        val u = raw.trim()
        if (u.isEmpty()) return ""
        // ignore JS/data/about pseudo-URLs
        if (u.startsWith("javascript:", ignoreCase = true)
            || u.startsWith("data:", ignoreCase = true)
            || u.startsWith("about:", ignoreCase = true)
        ) return ""
        // protocol-relative
        if (u.startsWith("//")) return "https:$u"
        // absolute already
        if (u.startsWith("http://") || u.startsWith("https://")) return u
        // try to resolve relative against document.baseUri()
        return try {
            val base = java.net.URL(document.baseUri())
            java.net.URL(base, u).toString()
        } catch (e: Exception) {
            // fallback: return as-is (may be resolved by extractors or fail later)
            u
        }
    }

    // check typical iframe attributes (src, data-src, data-lazy-src, data-embed, etc.)
    document.select("iframe").forEach { f ->
        val candidates = listOf("src", "data-src", "data-lazy-src", "data-embed", "data-srcset")
        var foundAttr: String? = null
        for (a in candidates) {
            // try absUrl first (resolves relative), then raw attr
            val abs = f.absUrl(a)
            if (abs.isNotBlank()) { foundAttr = abs; break }
            val raw = f.attr(a)
            if (raw.isNotBlank()) { foundAttr = raw; break }
        }
        // if no candidate found, also try attributes in case plugin uses other names
        if (foundAttr == null) {
            val src = f.attr("src")
            if (src.isNotBlank()) foundAttr = src
        }

        val n = foundAttr?.let { normalize(it) }.orEmpty()
        if (n.isNotBlank()) urls += n
    }

    // also look for <video> and <source> tags and common embed anchors
    document.select("video source[src], video[src]").forEach { s ->
        s.attr("src").takeIf { it.isNotBlank() }?.let { urls += normalize(it) }
    }

    document.select("a[href]").forEach { a ->
        val href = a.absUrl("href").ifBlank { a.attr("href") }
        // only add likely embed/player links (avoid all links)
        if (href.contains("player") || href.contains("embed") || href.contains("stream") || href.contains("cdn")) {
            normalize(href).takeIf { it.isNotBlank() }?.let { urls += it }
        }
    }

    // call extractor for each unique url
    for (u in urls) {
        try {
            // nếu loadExtractor là suspend và trả về Boolean bạn có thể kiểm tra kết quả
            val ok = loadExtractor(u, subtitleCallback, callback)
            if (ok) found = true
        } catch (e: Exception) {
            // bỏ qua lỗi 1 url, tiếp tục url khác
        }
    }

    return found
}
}