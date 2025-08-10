package com.GaypornHDfree

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Element

class GaypornHDfree : MainAPI() {
    override var mainUrl = "https://gaypornhdfree.com"
    override var name = "GaypornHDfree"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    // Thêm WebViewResolver để xử lý Cloudflare Turnstile
    override val webViewResolver = WebViewResolver(
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        interceptUrls = listOf(mainUrl),
        timeout = 60000
    )

    // Thêm request interceptor
    override val requestInterceptor: RequestInterceptor = object : RequestInterceptor {
        override suspend fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
                .newBuilder()
                .addHeader("Cookie", "i18next=en; age_gate=1")
                .addHeader("Referer", mainUrl)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.5")
                .build()
            return chain.proceed(request)
        }
    }

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
        val cleanPath = request.data.removePrefix("/").removeSuffix("/")
        val url = if (page == 1) "$mainUrl/$cleanPath"
        else "$mainUrl/$cleanPath/page/$page/"

        // Sử dụng webViewResolver để vượt Cloudflare
        val document = webViewResolver.getDocument(url, timeout = 60000)
        val home = document.select("div.videopost").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = selectFirst("a.thumb-video") ?: return null
        val href = anchor.attr("href").trim().ifEmpty { return null }

        val title = selectFirst("div.deno.video-title a")?.text()?.trim().orEmpty()
        // Lấy ảnh từ src hoặc data-src
        val posterUrl = selectFirst("a.thumb-video img")
            ?.let { it.attr("src").ifEmpty { it.attr("data-src") } }
            ?.trim().orEmpty()

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("div.deno.video-title a")?.text()?.trim().orEmpty()
        val href = this.selectFirst("a.thumb-video")?.attr("href")?.trim() ?: ""
        val posterUrl = this.selectFirst("a.thumb-video img")?.attr("src")?.trim() ?: ""

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val url = "$mainUrl/page/$i/?s=$query"
            // Sử dụng webViewResolver cho search
            val document = webViewResolver.getDocument(url, timeout = 60000)

            val results = document.select("div.videopost").mapNotNull { it.toSearchResult() }

            if (results.isNotEmpty()) {
                searchResponse.addAll(results)
            } else {
                break
            }
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        // Sử dụng webViewResolver cho load
        val document = webViewResolver.getDocument(url, timeout = 60000)

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val recommendations = document.select("div.videopost").mapNotNull {
            it.toRecommendResult()
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
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
    val videoUrls = mutableSetOf<String>()

    // Thu thập URL từ iframe (ưu tiên data-src trước, fallback sang src)
    document.select("iframe").forEach { iframe ->
        iframe.attr("data-src").takeIf { it.isNotBlank() }?.let(videoUrls::add)
            ?: iframe.attr("src").takeIf { it.isNotBlank() }?.let(videoUrls::add)
    }

    // Thu thập URL từ player
    document.select("div.video-player[data-src]").forEach {
        it.attr("data-src").takeIf { src -> src.isNotBlank() }?.let(videoUrls::add)
    }

    // Thu thập URL từ download button
    document.select("div.download-button-wrapper a[href]").forEach {
        it.attr("href").takeIf { href -> href.isNotBlank() }?.let(videoUrls::add)
    }

    // Xử lý tất cả URL đã thu thập
    videoUrls.forEach { url ->
        loadExtractor(url, subtitleCallback, callback)
    }

    return videoUrls.isNotEmpty()
    }
}
