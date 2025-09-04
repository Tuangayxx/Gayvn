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

private fun extractUrlsFromText(text: String): List<String> {
    val found = mutableSetOf<String>()
    val clean = text.replace("\\/", "/")

    // mp4 / m3u8
    Regex("(https?://[^\"'\\s<>]+\\.(?:m3u8|mp4))", RegexOption.IGNORE_CASE).findAll(clean).forEach {
        found.add(it.groupValues[1])
    }

    // iframe src
    Regex("<iframe[^>]+src=['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE).findAll(clean).forEach {
        found.add(it.groupValues[1])
    }

    // general src="..."
    Regex("src=['\"](https?://[^'\"]+)['\"]", RegexOption.IGNORE_CASE).findAll(clean).forEach {
        found.add(it.groupValues[1])
    }

    // file: "..."
    Regex("\"file\"\\s*[:=]\\s*['\"](https?://[^'\"]+)['\"]", RegexOption.IGNORE_CASE).findAll(clean).forEach {
        found.add(it.groupValues[1])
    }

    return found.filter { it.isNotBlank() }
}

/**
 * Trả về list Pair(url, referer) — referer có thể null (nếu không rõ).
 * Hàm robust: dò li.dooplay_player_option, các data-post/data-nume, onclick, scan toàn trang, parse API responses.
 */
private suspend fun fetchPlayerUrls(pageUrl: String): List<Pair<String, String?>> {
    val results = linkedSetOf<Pair<String, String?>>()
    val pageDoc = app.get(pageUrl).document
    val pageHtml = pageDoc.html()

    // detect player_api (dtAjax.player_api) nếu có
    val playerApi = Regex("\"player_api\"\\s*:\\s*\"([^\"]+)\"").find(pageHtml)?.groupValues?.get(1)
        ?: "$mainUrl/wp-json/dooplayer/v2/"

    // 1) chuẩn: li.dooplay_player_option[data-post][data-nume]
    val options = pageDoc.select("li.dooplay_player_option[data-post][data-nume]")
    if (options.isNotEmpty()) {
        for (opt in options) {
            val post = opt.attr("data-post").trim()
            val nume = opt.attr("data-nume").trim()
            if (post.isBlank() || nume.isBlank()) continue
            val apiUrl = if (playerApi.contains("?")) "$playerApi&id=$post&nume=$nume" else "$playerApi?id=$post&nume=$nume"
            try {
                // thêm referer + X-Requested-With để giống trình duyệt (nếu app.get hỗ trợ headers/ referer)
                val respText = try {
                    // nếu app.get có tham số referer/headers (một số project hỗ trợ), dùng nó:
                    app.get(apiUrl, referer = pageUrl, headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Accept" to "application/json, text/javascript, text/html, */*"
                    )).text
                } catch (e: Exception) {
                    // fallback: simple get
                    app.get(apiUrl).text
                }

                // parse response text ra url
                extractUrlsFromText(respText).forEach { u -> results.add(Pair(fixUrl(u), pageUrl)) }

                // nếu tìm thấy thì tiếp (không break, có thể có nhiều mirror)
            } catch (e: Exception) {
                // ignore per-mirror errors
            }
        }
    }

    // 2) tìm mọi cặp data-post/data-nume trên trang (nhiều theme khác nhau)
    val postNumeRegex = Regex("data-post=['\"]?(\\d+)['\"]?[^>]*data-nume=['\"]?(\\d+)['\"]?", RegexOption.IGNORE_CASE)
    postNumeRegex.findAll(pageHtml).forEach { m ->
        val post = m.groupValues[1]; val nume = m.groupValues[2]
        val apiUrl = if (playerApi.contains("?")) "$playerApi&id=$post&nume=$nume" else "$playerApi?id=$post&nume=$nume"
        try {
            val respText = app.get(apiUrl, referer = pageUrl).text
            extractUrlsFromText(respText).forEach { u -> results.add(Pair(fixUrl(u), pageUrl)) }
        } catch (_: Exception) {}
    }

    // 3) onclick/button patterns (player_load('id','nume') ...)
    val onclickRegex = Regex("player_?load\\([^\\)]*?(\\d{3,})[^\\d]*(\\d{1,2})[^\\)]*\\)", RegexOption.IGNORE_CASE)
    onclickRegex.findAll(pageHtml).forEach { m ->
        val post = m.groupValues[1]; val nume = m.groupValues[2]
        val apiUrl = if (playerApi.contains("?")) "$playerApi&id=$post&nume=$nume" else "$playerApi?id=$post&nume=$nume"
        try {
            val respText = app.get(apiUrl, referer = pageUrl).text
            extractUrlsFromText(respText).forEach { u -> results.add(Pair(fixUrl(u), pageUrl)) }
        } catch (_: Exception) {}
    }

    // 4) scan trực tiếp page HTML, script tags để tìm m3u8/mp4/iframe
    extractUrlsFromText(pageHtml).forEach { u -> results.add(Pair(fixUrl(u), pageUrl)) }
    pageDoc.select("script").forEach { s ->
        val data = s.data()
        extractUrlsFromText(data).forEach { u -> results.add(Pair(fixUrl(u), pageUrl)) }
    }

    // 5) nếu có iframe embed (ví dụ 5flix), thêm iframe src với referer = pageUrl (để extractor xử lý embed)
    pageDoc.select("iframe[src]").mapNotNull { el -> el.attr("src").takeIf { it.isNotBlank() } }
        .forEach { src -> results.add(Pair(fixUrl(src), pageUrl)) }

    return results.toList()
}

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    // data = trang phim gốc (referer gợi ý)
    val sources = fetchPlayerUrls(data).toMutableList()

    // fallback: nếu vẫn rỗng, dò iframe trực tiếp
    if (sources.isEmpty()) {
        val doc = app.get(data).document
        doc.select("iframe[src]").mapNotNull { el: Element -> el.attr("src").takeIf { s -> s.isNotBlank() } }
            .forEach { sources.add(Pair(fixUrl(it), data)) }
    }

    if (sources.isEmpty()) return false

    // ưu tiên m3u8 trước
    sources.sortWith(compareByDescending<Pair<String,String?>> { it.first.contains(".m3u8") }.thenBy { it.first })

    for ((url, referer) in sources) {
        try {
            // nếu url là trực tiếp m3u8, truyền referer (page gốc) để server không block
            val usedReferer = referer ?: data

            // sử dụng loadExtractor (tận dụng extractors đã có trong repo)
            loadExtractor(url, usedReferer, subtitleCallback) { link ->
                callback(link)
            }
        } catch (e: Exception) {
            // nếu extractor fail, thử tạo newExtractorLink tạm (nếu muốn)
            // optional: callback(newExtractorLink(...))
        }
    }

    return true
}
}
