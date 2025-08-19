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
        "$mainUrl/video-tag/onlyfans/" to "Onlyfans"
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
        val posterUrl = aTag.selectFirst(".aiovg-thumbnail img")?.attr("src")
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

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            if (actors.isNotEmpty()) addActors(actors)
        }
    }

    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val ua = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0")
    val document = app.get(data, headers = ua).document

    val scripts = document.select("script[type=application/ld+json]")
        .mapNotNull { it.data()?.trim() }
        .filter { it.isNotEmpty() }

    var link: String? = null

    try {
        for (jsonData in scripts) {
            try {
                if (jsonData.trimStart().startsWith("[")) {
                    val arr = JSONArray(jsonData)
                    for (i in 0 until arr.length()) {
                        val item = arr.get(i)
                        if (item is JSONObject) {
                            if (item.has("contentUrl")) { link = item.getString("contentUrl"); break }
                            if (item.has("video") && item.get("video") is JSONObject) {
                                val v = item.getJSONObject("video")
                                if (v.has("contentUrl")) { link = v.getString("contentUrl"); break }
                            }
                        }
                    }
                } else {
                    val obj = JSONObject(jsonData)
                    if (obj.has("@graph")) {
                        val graph = obj.getJSONArray("@graph")
                        for (i in 0 until graph.length()) {
                            val g = graph.get(i)
                            if (g is JSONObject) {
                                if (g.has("contentUrl")) { link = g.getString("contentUrl"); break }
                                if (g.has("video") && g.get("video") is JSONObject) {
                                    val v = g.getJSONObject("video")
                                    if (v.has("contentUrl")) { link = v.getString("contentUrl"); break }
                                }
                            }
                        }
                        if (link != null) break
                    }
                    if (link == null) {
                        if (obj.has("contentUrl")) { link = obj.getString("contentUrl") }
                        else if (obj.has("video") && obj.get("video") is JSONObject) {
                            val v = obj.getJSONObject("video")
                            if (v.has("contentUrl")) link = v.getString("contentUrl")
                        }
                    }
                }
            } catch (_: Exception) {
                // tiếp tục script tiếp theo
            }

            if (link == null) {
                // fallback: tìm trực tiếp mp4 trong json text
                val regex = Regex("https?://[^\\s\"']+\\.mp4")
                val match = regex.find(jsonData)
                if (match != null) link = match.value
            }

            if (link != null) break
        }
    } catch (e: Exception) {
        return false
    }

    link = link?.replace("\\/", "/")
    if (link == null) return false

    loadExtractor(
        link,
        "$mainUrl/",
        subtitleCallback,
        callback
    )
    return true
}
}
