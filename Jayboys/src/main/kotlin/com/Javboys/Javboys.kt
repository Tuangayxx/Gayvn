package com.Jayboys

import android.util.Log
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import org.jsoup.Jsoup
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response

class Jayboys : MainAPI() {
    override var mainUrl = "https://javboys.tv"
    override var name = "Javboys"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    // override var sequentialMainPageDelay       = 250L // ? 0.25 saniye
    // override var sequentialMainPageScrollDelay = 250L // ? 0.25 saniye

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.select("title").text() == "Just a moment..." || doc.select("title").text() == "Bir dakika lütfen...") {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    val cookies = mapOf("i18next" to "en")

    override val mainPage = mainPageOf(
        "/2025/" to "Latest Updates",
        "/category/onlyfans/" to "Onlyfans",
        "/category/movies/" to "Movies",
        "/category/asian-gay-porn-hd/" to "Châu Á",
        "/category/western-gay-porn-hd/" to "Châu Mỹ Âu",
        "/category/%e3%82%b2%e3%82%a4%e9%9b%91%e8%aa%8c/" to "Tạp chí",
        "/category/hunk-channel/" to "Hunk Channel",
    )

     override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "${request.data}page/$page"
        } else {
            "$mainUrl${request.data}"
        }

        val document = app.get(url).document
        val home = document.select("div.video col-2").mapNotNull { it.toSearchResult() }

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
        val title = this.select("span.title").text()
        val href = this.select("a.thumb-video").attr("href")
        val posterUrl = this.selectFirst("a.thumb-video img")?.attr("src")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..7) {
            val document = app.get("$mainUrl/search/video/?s=$query&page=$i").document
            val results = document.select("div.video.col-2").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
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

        // Player links
        document.select("div.video-player").forEach { player ->
            val videoUrl = player.attr("data-src").takeIf { it.isNotBlank() }
            videoUrl?.let { url ->
                found = true
                loadExtractor(url, subtitleCallback, callback)
            }
        }

        // Download button links
        document.select("div.download-button-wrapper").forEach { down ->
            val videoLink = down.attr("href").takeIf { it.isNotBlank() }
            videoLink?.let { url ->
                found = true
                loadExtractor(url, subtitleCallback, callback)
            }
        }

        return found
    }
}