package com.GXtapes

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.io.IOException

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

    // Sử dụng appUtils mặc định với headers tùy chỉnh
    private val app = AppUtils.createClient(
        headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Referer" to "https://gay.xtapes.in/",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "DNT" to "1"
        )
    )

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
        
        val document = app.get(url).document
        val home = document.select("ul.listing-tube li").mapNotNull { it.toSearchResult() }

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
            val document = app.get("$mainUrl/page/$i/?s=$query").document
            val results = document.select("ul.listing-tube li").mapNotNull { it.toSearchResult() }

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

        document.select("#video-code iframe").forEach { iframe ->
            val src = iframe.attr("src")
            when {
                src.contains("74k.io") -> {
                    val decodedUrl = "https://74k.io/e/" + src.substringAfterLast("/")
                    found = found or loadExtractor(decodedUrl, subtitleCallback, callback)
                }
                src.contains("88z.io") -> {
                    found = found or extract88zLink(src, subtitleCallback, callback)
                }
                else -> {
                    found = found or loadExtractor(src, subtitleCallback, callback)
                }
            }
        }

        return found
    }

    private suspend fun extract88zLink(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            // Tải nội dung iframe
            val iframeDoc = app.get(url).document
            
            // Tìm thẻ script chứa dữ liệu video
            val scriptContent = iframeDoc.select("script").find { script ->
                script.data().contains("media-player") || 
                script.data().contains("sources")
            }?.data() ?: return false
            
            // Trích xuất URL HLS từ dữ liệu JSON
            val videoRegex = Regex("""sources\s*:\s*\[\s*\{\s*src\s*:\s*['"]([^'"]+)['"]""")
            val videoUrl = videoRegex.find(scriptContent)?.groupValues?.get(1)
                ?: return false

            // Kiểm tra định dạng video
            val isM3u8 = videoUrl.contains(".m3u8")
            val quality = Qualities.Unknown.value
            
            // Trả về link video
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "Vidstack Player",
                    url = videoUrl,
                    referer = mainUrl,
                    quality = quality,
                    type = if (isM3u8) ExtractorLinkType.HLS else ExtractorLinkType.VIDEO
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}