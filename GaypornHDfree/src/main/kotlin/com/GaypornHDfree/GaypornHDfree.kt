package com.GaypornHDfree

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Element
import java.lang.Exception

class GaypornHDfree : MainAPI() {
    override var mainUrl = "https://gaypornhdfree.com"
    override var name = "GaypornHDfree"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    // Define custom headers for requests
    private val customHeaders = mapOf(
        "Cookie" to "age_gate=1; i18next=en",
        "Referer" to mainUrl,
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Connection" to "keep-alive",
        "Upgrade-Insecure-Requests" to "1"
    )

    override val mainPage = mainPageOf(
        "" to "Mới cập nhật",
        "category/onlyfans/" to "Onlyfans",
        "category/gay-porn-movies/" to "Phim dài",
        "category/asian-gay-porn-hd/" to "Châu Á",
        "category/bilatinmen/" to "La tin cu bự",
        "category/fraternityx/" to "Fraternity X",
        "category/sketchysex/" to "Sketchy Sex",
        "2025/07/" to "Video 7",
        "2025/06/" to "Video 6",
        "2025/05/" to "Video 5",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        try {
            val cleanPath = request.data.removePrefix("/").removeSuffix("/")
            val url = if (page == 1) "$mainUrl/$cleanPath" else "$mainUrl/$cleanPath/page/$page/"
            val document = app.get(url, headers = customHeaders).document
            val home = document.select("div.videopost").mapNotNull { it.toSearchResult() }

            return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = home,
                    isHorizontalImages = true
                ),
                hasNext = document.selectFirst("a.next") != null
            )
        } catch (e: Exception) {
            Log.e("GaypornHDfree", "Error in getMainPage: ${e.message}")
            throw e
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = selectFirst("a.thumb-video") ?: return null
        val href = anchor.attr("href").trim().ifEmpty { return null }
        val title = selectFirst("div.deno.video-title a")?.text()?.trim() ?: return null
        val posterUrl = selectFirst("a.thumb-video img")
            ?.let { it.attr("src").ifEmpty { it.attr("data-src") } }
            ?.trim() ?: return null

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        return toSearchResult() // Reuse toSearchResult logic
    }

    override suspend fun load(url: String): LoadResponse {
        try {
            val document = app.get(url, headers = customHeaders).document
            val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                ?: throw IllegalStateException("Title not found")
            val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
            val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

            val recommendations = document.select("div.videopost").mapNotNull {
                it.toRecommendResult()
            }

            return newMovieLoadResponse(title, url, TvType.NSFW, url) {
                this.posterUrl = poster
                this.plot = description
                this.recommendations = recommendations
            }
        } catch (e: Exception) {
            Log.e("GaypornHDfree", "Error in load: ${e.message}")
            throw e
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val document = app.get(data, headers = customHeaders).document
            val videoUrls = mutableSetOf<String>()

            // Collect URLs from iframe
            document.select("iframe").forEach { iframe ->
                iframe.attr("data-src").takeIf { it.isNotBlank() && it.startsWith("http") }?.let(videoUrls::add)
                    ?: iframe.attr("src").takeIf { it.isNotBlank() && it.startsWith("http") }?.let(videoUrls::add)
            }

            // Collect URLs from player
            document.select("div.video-player[data-src]").forEach {
                it.attr("data-src").takeIf { src -> src.isNotBlank() && src.startsWith("http") }?.let(videoUrls::add)
            }

            // Collect URLs from download button
            document.select("div.download-button-wrapper a[href]").forEach {
                it.attr("href").takeIf { href -> href.isNotBlank() && href.startsWith("http") }?.let(videoUrls::add)
            }

            if (videoUrls.isEmpty()) {
                Log.w("GaypornHDfree", "No video URLs found for $data")
                return false
            }

            // Process collected URLs
            videoUrls.forEach { url ->
                try {
                    loadExtractor(url, subtitleCallback, callback)
                } catch (e: Exception) {
                    Log.e("GaypornHDfree", "Failed to load extractor for $url: ${e.message}")
                }
            }

            return true
        } catch (e: Exception) {
            Log.e("GaypornHDfree", "Error in loadLinks: ${e.message}")
            return false
        }
    }
}
