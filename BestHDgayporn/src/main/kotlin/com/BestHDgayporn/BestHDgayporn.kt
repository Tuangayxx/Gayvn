package com.BestHDgayporn

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.nodes.Element
import org.json.JSONObject
import org.json.JSONArray
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BestHDgayporn : MainAPI() {
    override var mainUrl = "https://besthdgayporn.com"
    override var name = "BestHDgayporn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest",
        "$mainUrl/video-tag/men-com/" to "MEN.com",
        "$mainUrl/video-tag/bareback-gay-porn/" to "Bareback",
        "$mainUrl/video-tag/onlyfans/" to "Onlyfans",
        "$mainUrl/video-tag/latino/" to "Latino",
        "$mainUrl/video-tag/voyr/" to "Voyr",
        "$mainUrl/video-tag/chaos-men/" to "Chaos Men",
        "$mainUrl/video-tag/nakedsword/" to "Naked Sword",
    )

    private val cookies = mapOf(Pair("hasVisited", "1"), Pair("accessAgeDisclaimerPH", "1"))

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else request.data
        val ua = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0")
        val document = app.get(url, headers = ua).document
        val home = document.select("div.aiovg-item-video").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = this.selectFirst("a") ?: return null
        val href = aTag.attr("href")
        val posterUrl = this.select("img").attr("src")
        val title = this.selectFirst(".aiovg-link-title")?.text()?.trim() ?: "No Title"

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val aTag = this.selectFirst("a") ?: return null
        val href = aTag.attr("href")
        val posterUrl = this.select("img").attr("src")
        val title = this.selectFirst(".aiovg-link-title")?.text()?.trim() ?: "No Title"

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        val items = document.select("div.aiovg-item-video")
        return items.mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val ua = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0")
        val doc = app.get(url, headers = ua).document

        val title = doc.selectFirst("meta[property='og:title']")?.attr("content") ?: doc.title()

        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content") ?: ""

        val description = doc.selectFirst("meta[property='og:description']")?.attr("content") ?: ""

        val actors = listOf("Flynn Fenix", "Nicholas Ryder").filter { title.contains(it) }

        val recommendations = doc.select("div.aiovg-item-tag").mapNotNull {
            it.toRecommendResult()
    }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            if (actors.isNotEmpty()) addActors(actors)
            this.recommendations = recommendations
        }
    }

        override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val headers = mapOf("User-Agent" to "Mozilla/5.0", "Referer" to data)
    val res = app.get(data, headers = headers)
    val doc = res.document

    val urlRegex = Regex("""https?://[^\s'"]+?\.(?:mp4|m3u8|webm)(\?[^'"\s<>]*)?""", RegexOption.IGNORE_CASE)
    val found = mutableListOf<String>()

    // 1) JSON-LD contentUrl
    doc.select("script[type=application/ld+json]").forEach { s ->
        urlRegex.findAll(s.data()).forEach { m -> found.add(m.value) }
    }

    // 2) video / source / data- attributes
    doc.select("video[src], video > source[src], source[src], video[data-src], source[data-src]").forEach { e ->
        val v = e.attr("abs:src").ifEmpty { e.attr("abs:data-src") }
        if (v.isNotBlank()) found.add(v)
    }

    // 3) iframe embed -> fetch embed and scan
    doc.select("iframe[src]").mapNotNull { it.attr("abs:src").takeIf { it.isNotBlank() } }.forEach { iframeUrl ->
        try {
            val iframeDoc = app.get(iframeUrl, headers = headers).document
            urlRegex.findAll(iframeDoc.html()).forEach { m -> found.add(m.value) }
            iframeDoc.select("video[src], source[src]").forEach { el ->
                val v = el.attr("abs:src").ifEmpty { el.attr("abs:data-src") }
                if (v.isNotBlank()) found.add(v)
            }
        } catch (e: Exception) {
            // ignore iframe fetch errors
        }
    }

    // 4) fallback: scan whole HTML
    urlRegex.findAll(doc.html()).forEach { found.add(it.value) }

    // Normalize + dedupe
    val candidates = found.map { it.trim().replace("&amp;", "&").replace(" ", "%20") }
        .filter { it.isNotBlank() }
        .distinct()

    if (candidates.isEmpty()) {
        println("DEBUG: No video links found for $data")
        return false
    }

    // Emit mỗi link
    candidates.forEachIndexed { i, url ->
        val friendlyName = when {
            url.contains("aucdn.net", ignoreCase = true) -> "CDN"
            url.contains("besthdgayporn.com", ignoreCase = true) -> "Origin"
            else -> "Direct"
        }
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "$friendlyName ${i + 1}",
                url = url
            ) {
                this.referer = data
                this.quality = getQualityFromName(url) ?: Qualities.Unknown.value
                this.headers = headers
            }
        )
    }

    return true
}
}
    
    