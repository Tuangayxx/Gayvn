package com.GXtapes

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.io.IOException
import okhttp3.OkHttpClientimport com.lagradost.cloudstream3.mvvm.safeApiCall


class GXtapes : MainAPI() {
    override var mainUrl = "https://g.xtapes.in"
    override var name = "G_Xtapes"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    // Client với headers để vượt qua kiểm tra
    private val client by lazy {
        app.createClient {
            addHeaders(
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                    "Referer" to "https://gay.xtapes.in/",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.9",
                    "DNT" to "1"
                )
            )
        }
    }

    override val mainPage = mainPageOf(
        "" to "Latest",
        "/68780" to "BelAmi",
        "/62478" to "FraternityX",
        "/416537" to "Falcon Studio",
        "/627615" to "Onlyfans",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.isEmpty()) {
            "$mainUrl/page/$page/"
        } else {
            "$mainUrl${request.data}/page/$page/"
        }
        
        val document = client.get(url).document
        val home = document.select("ul.listing-tube li").mapNotNull { element ->
            safeApiCall { element.toSearchResult() }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("img").attr("title")
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = client.get("$mainUrl/page/$i/?s=$query").document
            val results = document.select("ul.listing-tube li").mapNotNull { element ->
                safeApiCall { element.toSearchResult() }
            }

            if (results.isEmpty()) break
            
            results.forEach { newItem ->
                if (searchResponse.none { it.url == newItem.url }) {
                    searchResponse.add(newItem)
                }
            }
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = client.get(url).document

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
        val document = client.get(data).document
        var found = false

        // Debug: Hiển thị toàn bộ HTML để kiểm tra
        Log.d("HTML_DEBUG", document.outerHtml().take(5000))

        val iframes = document.select("#video-code iframe")
        Log.d("IFRAME_COUNT", "Found ${iframes.size} iframes")

        iframes.forEachIndexed { index, iframe ->
            val src = iframe.attr("src")
            Log.d("IFRAME_$index", src)

            // Thử cả 3 cách xử lý link
            found = found or tryDirectExtractor(src, subtitleCallback, callback)
                    or try88zExtractor(src, subtitleCallback, callback)
                    or try74kExtractor(src, subtitleCallback, callback)
        }

        return found
    }

    private suspend fun tryDirectExtractor(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return safeApiCall {
            loadExtractor(url, subtitleCallback, callback)
        } ?: false
    }

    private suspend fun try88zExtractor(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (!url.contains("88z.io")) return false

        return safeApiCall {
            val directUrl = "https://88z.io/getvid/" + url.substringAfter("#")
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "88z.io",
                    url = directUrl,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.VIDEO
                )
            )
            true
        } ?: false
    }

    private suspend fun try74kExtractor(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (!url.contains("74k.io")) return false

        return safeApiCall {
            val decodedUrl = "https://74k.io/e/" + url.substringAfterLast("/")
            loadExtractor(decodedUrl, subtitleCallback, callback)
        } ?: false
    }
}