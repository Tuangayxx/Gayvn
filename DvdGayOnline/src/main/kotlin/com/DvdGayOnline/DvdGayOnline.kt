package com.DvdGayOnline

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import com.lagradost.api.Log


class DvdGayOnline : MainAPI() {
    override var mainUrl = "https://dvdgayonline.com"
    override var name = "DvdGayOnline"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false 
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        "/genre/new/"                            to "Latest",
        "/genre/new-release/"                    to "New release",
        "/tendencia/"                            to "Trending",
        "/genre/asian/"                          to "Asian",
        "/genre/fratboys/"                       to "Fratboys",
        "/genre/group-sex/"                      to "Group",
        "/genre/gangbang/"                       to "Gangbang",
        "/genre/parody/"                         to "Parody",
        "/genre/latino/"                         to "Latino",
    )    

// ---------------- getMainPage ----------------
override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val pageUrl = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}page/$page/"
    val document = app.get(pageUrl).document

    // Lấy các <article class="item"> bên trong div.items (có thể có class normal)
    val elements = document.select("div.items.normal article.item, div.items article.item")

    val home = elements.mapNotNull { element: Element ->
        element.toSearchResult()
    }

    // detect pagination (nếu có link trang tiếp)
    val hasNext = document.select("div.pagination a").isNotEmpty()

    return newHomePageResponse(
        list = HomePageList(
            name = request.name,
            list = home,
            isHorizontalImages = false
        ),
        hasNext = hasNext
    )
}

// ---------- toSearchResult ----------
private fun Element.toSearchResult(): SearchResponse? {
    // Lấy title (ưu tiên div.data h3 a)
    val titleElement = this.selectFirst("div.data h3 a")
    val title = titleElement?.text()?.trim().orEmpty()

    // Lấy href: ưu tiên poster a, fallback về data h3 a
    val posterAnchorHref = this.selectFirst("div.poster a")?.attr("href")?.takeIf { it.isNotBlank() }
    val dataHref = titleElement?.attr("href")?.takeIf { it.isNotBlank() }
    val hrefAttr = posterAnchorHref ?: dataHref ?: return null
    val href = fixUrl(hrefAttr)

    // Lấy poster (xử lý lazy attrs)
    val imgEl = this.selectFirst("div.poster img") ?: this.selectFirst("img")
    val posterRaw = imgEl?.attr("data-src")?.takeIf { it.isNotBlank() }
        ?: imgEl?.attr("data-lazy")?.takeIf { it.isNotBlank() }
        ?: imgEl?.attr("data-original")?.takeIf { it.isNotBlank() }
        ?: imgEl?.attr("src")?.takeIf { it.isNotBlank() }
    val posterUrl = posterRaw?.let { fixUrlNull(it) }

    return newMovieSearchResponse(title, href, TvType.NSFW) {
        this.posterUrl = posterUrl
    }
}

// ---------- load (metadata) ----------
override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document

    val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
        ?: document.selectFirst("h1")?.text()?.trim()
        ?: ""

    val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        ?: document.selectFirst("div.poster img")?.attr("data-src")?.takeIf { it.isNotBlank() }
        ?: document.selectFirst("div.poster img")?.attr("src")?.trim()

    val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        ?: document.selectFirst("div.text, div.entry-content, div.post-content")?.text()?.trim()
        ?: ""

    return newMovieLoadResponse(title, url, TvType.NSFW, url) {
        this.posterUrl = poster
        this.plot = description
    }
}

// tìm player_api trong JS (nếu có) hoặc fallback về mainUrl/wp-json/dooplayer/v2/
private fun detectPlayerApi(documentHtml: String): String {
    val apiFromJs = Regex("\"player_api\"\\s*:\\s*\"([^\"]+)\"").find(documentHtml)?.groups?.get(1)?.value
    if (!apiFromJs.isNullOrBlank()) return apiFromJs
    // fallback
    return "$mainUrl/wp-json/dooplayer/v2/"
}

private fun extractUrlsFromText(text: String): List<String> {
    val found = mutableSetOf<String>()
    // unwrap escaped \/ -> /
    val clean = text.replace("\\/", "/")

    // common file urls
    val urlRegex = Regex("(https?://[^\"'\\s<>]+\\.(?:m3u8|mp4))", RegexOption.IGNORE_CASE)
    urlRegex.findAll(clean).forEach { found.add(it.groupValues[1]) }

    // iframe src occurrences
    val iframeRegex = Regex("<iframe[^>]+src=['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
    iframeRegex.findAll(clean).forEach { found.add(it.groupValues[1]) }

    // general src="..."
    val srcRegex = Regex("src=['\"](https?://[^'\"]+)['\"]", RegexOption.IGNORE_CASE)
    srcRegex.findAll(clean).forEach { found.add(it.groupValues[1]) }

    // file: "..."
    val fileRegex = Regex("\"file\"\\s*[:=]\\s*['\"](https?://[^'\"]+)['\"]", RegexOption.IGNORE_CASE)
    fileRegex.findAll(clean).forEach { found.add(it.groupValues[1]) }

    return found.filter { it.isNotBlank() }
}

private suspend fun fetchPlayerUrls(pageUrl: String): List<String> {
    val urls = linkedSetOf<String>() // preserve order + unique
    val pageDoc = app.get(pageUrl).document
    val pageHtml = pageDoc.html()

    // 1) detect player_api base
    val playerApiBase = detectPlayerApi(pageHtml)

    // 2) try standard li.dooplay_player_option[data-post][data-nume]
    val options = pageDoc.select("li.dooplay_player_option[data-post][data-nume]")
    if (options.isNotEmpty()) {
        for (opt in options) {
            val post = opt.attr("data-post").trim()
            val nume = opt.attr("data-nume").trim()
            if (post.isBlank() || nume.isBlank()) continue
            val apiUrl = if (playerApiBase.contains("?")) "$playerApiBase&id=$post&nume=$nume" else "$playerApiBase?id=$post&nume=$nume"
            try {
                // Nếu cần, thêm headers (Referer) — uncomment / adapt nếu API chặn
                // val resp = app.get(apiUrl).header("Referer", pageUrl).text
                val resp = app.get(apiUrl).text
                extractUrlsFromText(resp).forEach { urls.add(fixUrl(it)) }
            } catch (_: Exception) { /* ignore per-mirror errors */ }
        }
        if (urls.isNotEmpty()) return urls.toList()
    }

    // 3) try finding data-post / data-nume pairs anywhere in the page HTML (some themes put them on buttons)
    val postNumeRegex = Regex("data-post=['\"]?(\\d+)['\"]?[^>]*data-nume=['\"]?(\\d+)['\"]?", RegexOption.IGNORE_CASE)
    postNumeRegex.findAll(pageHtml).forEach { m ->
        val post = m.groupValues[1]; val nume = m.groupValues[2]
        val apiUrl = if (playerApiBase.contains("?")) "$playerApiBase&id=$post&nume=$nume" else "$playerApiBase?id=$post&nume=$nume"
        try {
            val resp = app.get(apiUrl).text
            extractUrlsFromText(resp).forEach { urls.add(fixUrl(it)) }
        } catch (_: Exception) {}
    }
    if (urls.isNotEmpty()) return urls.toList()

    // 4) try onclick/button patterns (onclick="player_load( '123','1' )" or similar)
    val onclickRegex = Regex("onclick\\s*=\\s*['\"][^'\"<>]*?(\\d{3,})[^'\"<>]*(\\d{1,2})[^'\"<>]*['\"]", RegexOption.IGNORE_CASE)
    onclickRegex.findAll(pageHtml).forEach { m ->
        val post = m.groupValues[1]; val nume = m.groupValues[2]
        val apiUrl = if (playerApiBase.contains("?")) "$playerApiBase&id=$post&nume=$nume" else "$playerApiBase?id=$post&nume=$nume"
        try {
            val resp = app.get(apiUrl).text
            extractUrlsFromText(resp).forEach { urls.add(fixUrl(it)) }
        } catch (_: Exception) {}
    }
    if (urls.isNotEmpty()) return urls.toList()

    // 5) fallback: scan the whole page HTML for direct mp4/m3u8 or iframes
    extractUrlsFromText(pageHtml).forEach { urls.add(fixUrl(it)) }
    if (urls.isNotEmpty()) return urls.toList()

    // 6) last resort: check inline scripts (some players build sources via JS variables)
    pageDoc.select("script").forEach { scriptEl ->
        val scriptText = scriptEl.data()
        extractUrlsFromText(scriptText).forEach { urls.add(fixUrl(it)) }
    }

    return urls.toList()
}

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val videoUrls = fetchPlayerUrls(data).toMutableList()

    // nếu vẫn rỗng, thử tìm iframe trên trang
    if (videoUrls.isEmpty()) {
        val doc = app.get(data).document
        doc.select("iframe[src]").mapNotNull { e -> e.attr("src").takeIf { s -> s.isNotBlank() } }
            .forEach { videoUrls.add(fixUrl(it)) }
    }

    if (videoUrls.isEmpty()) {
        // debug help: log small sample of page HTML to understand structure
        // println("DvdGayOnline: no video urls found for $data")
        return false
    }

    // ưu tiên HLS
    videoUrls.sortByDescending { it.contains(".m3u8") }

    for (videoUrl in videoUrls) {
        try {
            // chuyển tiếp cho hệ thống extractor hiện có
            loadExtractor(videoUrl, data, subtitleCallback) { extractorLink -> callback(extractorLink) }
        } catch (e: Exception) {
            // optional: println("loadExtractor failed for $videoUrl: ${e.message}")
        }
    }

    return true
}
}
