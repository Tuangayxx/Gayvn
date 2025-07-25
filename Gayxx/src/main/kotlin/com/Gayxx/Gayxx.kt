package com.Gayxx

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Element
import java.io.IOException
import com.lagradost.api.Log


class Gayxx : MainAPI() {
    override var mainUrl = "https://gayxx.net"
    override var name = "Gayxx"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        "/2025/07" to "Latest",
        "/2025/06" to "Tháng sáu",
        "/2025/05" to "Tháng năm",
        "/2025/04" to "Tháng bốn",
        "/2025/03" to "Tháng ba",
        "/2025/02" to "Tháng hai",
        "/2025/01" to "Tháng một",
        "/2024"    to "Năm ngoái",
        "/hottest-gay-porn-sex" to "Hot Videos",
        "/category/group" to "Càng đông càng vui",
        "category/gay-viet" to "Việt Nam ngày nay",
        "/category/sex-gay-onlyfans-chinese" to "Tung Của",
    )    

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
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


    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("a").attr("title")
        val href = fixUrl(this.select("a").attr("href"))
        val imgTag = this.selectFirst("img")
        val posterUrl = fixUrlNull(
            imgTag?.attr("data-src").takeIf { !it.isNullOrBlank() }
                ?: imgTag?.attr("src")
        )
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

   override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("div.videopost").mapNotNull { it.toSearchResult() }

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

    val supportedDomains = listOf(
            "bigwarp.io", "voe.sx", "mixdrop", 
            "streamtape", "doodstream.com", "dooood.com", "vinovo.to",
            "vide0.net",  "abyss.to", "vinovo.to", // Thêm domain Doodstream thực tế Thêm domain download
        )

    val document = app.get(data).document

    // Chỉ lấy các iframe trong khối .videohere (tránh quảng cáo sidebar)
    val iframes = document.select("div.videohere iframe[src]")
        .mapNotNull { it.attr("src").takeIf { src -> src.isNotBlank() } }
        .filter { url -> supportedDomains.any { domain -> domain in url } }

    if (iframes.isEmpty()) return false

    iframes.forEach { url ->
        Log.i("Tuangayxx", "Found iframe: $url")
        loadExtractor(url, subtitleCallback, callback)
    }
    return true
}
}