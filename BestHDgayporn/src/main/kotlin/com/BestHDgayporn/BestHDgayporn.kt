package com.BestHDgayporn

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class BestHDgayporn : MainAPI() {
    // Thông tin cơ bản về nhà cung cấp
    override var mainUrl = "https://besthdgayporn.com"
    override var name = "BestHDgayporn"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    // Danh mục trang chính
    override val mainPage = mainPageOf(
        "" to "Latest",
        "/video-tag/onlyfans" to "Onlyfans",
        "/video-tag/men-com" to "MEN.com",
        "/video-tag/corbin-fisher" to "Corbin Fisher",
        "/video-tag/raw-fuck-club" to "Raw fuck club",
        "/video-tag/randy-blue" to "Randy Blue",
        "/video-tag/sean-cody" to "Sean Cody",
        "/video-tag/falcon-studios" to "Falcon Studio",
        "/video-tag/voyr" to "Voyr",
        "/video-tag/next-door-studios" to "Next Door Studios",
        "/video-tag/noir-male" to "Noir Male",
        "/video-tag/asg-max" to "ASG Max",
    )

    // Lấy dữ liệu cho trang chính
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.isBlank()) {
            "$mainUrl/page/$page/"
        } else {
            "$mainUrl${request.data}/page/$page/"
        }

        val document = app.get(url).document
        // Sử dụng selector chính xác để tìm các video
        [cite_start]val items = document.select("div.aiovg-item-video") [cite: 216, 232, 249, 268]
        val videos = items.mapNotNull { it.toSearchResult() }
        
        // Kiểm tra xem có trang tiếp theo không
        [cite_start]val hasNext = document.selectFirst("a.next.page-numbers") != null [cite: 825]

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = videos,
                isHorizontalImages = true
            ),
            hasNext = hasNext
        )
    }

    // Chuyển đổi một phần tử HTML thành SearchResponse
    private fun Element.toSearchResult(): SearchResponse? {
        // Lấy link và tiêu đề từ phần tử a trong caption
        [cite_start]val linkElement = this.selectFirst("div.aiovg-caption a.aiovg-link-title") ?: return null [cite: 229, 246]
        val href = fixUrl(linkElement.attr("href"))
        val title = linkElement.text().trim()
        
        // Lấy ảnh poster, ưu tiên "data-src" cho lazy-load
        val imgElement = this.selectFirst("img.aiovg-responsive-element")
        val posterUrl = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: imgElement?.attr("src")

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    // Tìm kiếm video
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        val items = document.select("div.aiovg-item-video")
        return items.mapNotNull { it.toSearchResult() }
    }

    // Tải thông tin chi tiết của video
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        // Sử dụng meta tags để lấy thông tin
        [cite_start]val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "No title" [cite: 15]
        [cite_start]val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim() [cite: 37]
        [cite_start]val plot = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim() [cite: 17]
        
        // Tìm iframe của player
        [cite_start]val playerIframe = document.selectFirst("div.aiovg-player iframe") [cite: 142]
        val episodeUrl = playerIframe?.attr("src")

        // Tạo một tập phim duy nhất với URL của iframe
        val episodes = if (episodeUrl != null) {
            listOf(Episode(data = episodeUrl, name = "Play"))
        } else {
            emptyList()
        }

        return newTvSeriesLoadResponse(title, url, TvType.NSFW, episodes) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    // Tải link stream của video
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 'data' bây giờ là URL của iframe
        val document = app.get(data).document
        
        // Tìm các source video trong iframe
        document.select("source[src]").forEach {
            val src = it.attr("src")
            if (src.isNotBlank()) {
                loadExtractor(src, subtitleCallback, callback)
            }
        }
        return true
    }
}