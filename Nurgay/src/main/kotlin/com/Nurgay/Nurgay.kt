package com.Nurgay

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.nodes.Element
import java.io.IOException
import com.lagradost.api.Log


class Nurgay : MainAPI() {
    override var mainUrl = "https://nurgay.to"
    override var name = "Nurgay"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        "/?filter=latest"                         to "Latest",
        "/?filter=most-viewed"                    to "Most Viewed",
        "/asiaten"                                to "Asian",
        "/gruppensex"                             to "Group Sex",
        "/bisex"                                  to "Bisexual",
        "/hunks"                                  to "Hunks",
        "/latino"                                 to "Latino",
        "/muskeln"                                to "Muscle",
        "/bareback"                               to "Bareback",
    )    

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val pageUrl = if (page == 1) 
        "$mainUrl${request.data}" 
    else 
        "$mainUrl/page/$page${request.data}" // ✅ Sửa pagination

    val document = app.get(pageUrl).document
    val home = document.select("article.loop-video").mapNotNull { it.toSearchResult() }

    return newHomePageResponse(
        list = HomePageList(
            name = request.name,
            list = home,
            isHorizontalImages = false
        ),
        hasNext = true
    )
}

private fun Element.toSearchResult(): SearchResponse {
    val title = this.select("header.entry-header span").text() // ✅ Sửa lấy text
    val href = fixUrl(this.select("a").attr("href"))
    val posterUrl = fixUrlNull(this.select("img").attr("data-src"))
    
    return newMovieSearchResponse(title, href, TvType.NSFW) {
        this.posterUrl = posterUrl
    }
}

override suspend fun search(query: String): List<SearchResponse> {
    val searchResponse = mutableListOf<SearchResponse>()

    for (i in 1..7) {
        // ✅ Sửa URL search: thêm `&page=i`
        val document = app.get("$mainUrl/?s=$query&page=$i").document
        val results = document.select("article.loop-video").mapNotNull { it.toSearchResult() }

        if (results.isEmpty()) break
        searchResponse.addAll(results)
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
    val headers = mapOf("User-Agent" to "Mozilla/5.0", "Referer" to data)
    val res = app.get(data, headers = headers)
    val document = res.document // nếu lib của bạn dùng `res.document` thì đổi sang đó

    val urlRegex = Regex("""https?://[^\s'"]+?\.(?:mp4|m3u8|webm)(\?[^'"\s<>]*)?""", RegexOption.IGNORE_CASE)
    val videoUrls = mutableListOf<String>()

    // Thu thập URL từ iframe (ưu tiên data-src trước, fallback sang src)
    document.select("iframe").forEach { iframe ->
        iframe.attr("data-src").takeIf { it.isNotBlank() }?.let { videoUrls.add(it) }
            ?: iframe.attr("src").takeIf { it.isNotBlank() }?.let { videoUrls.add(it) }
    }

    // Tìm URL trực tiếp trong toàn bộ HTML (script, data-attr, ...)
    urlRegex.findAll(document.html()).forEach { match ->
        videoUrls.add(match.value)
    }

    // Loại bỏ trùng lặp và lọc rỗng
    val candidates = videoUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    // Gọi callback cho từng link tìm được
    candidates.forEachIndexed { i, url ->
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "Nur ${i + 1}",
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
    class VoeExtractor : Voe()  {
        override var mainUrl = "https://voe.sx"
        override var name = "VoeExtractor"
    }

    class Voesx : Voesx()  {
        override var mainUrl = "https://voe.sx"
        override var name = "Voesx"
    }

    class Bigwarp : Bigwarp()  {
        override var mainUrl = "https://bigwarp.io"
        override var name = "Bigwarp"
    }

    class dsio : DoodLaExtractor()  {
        override var mainUrl = "https://d-s.io"
        override var name = "dsio"
    }

}
