package com.iGay69

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.nodes.Element
import java.io.IOException
import com.lagradost.api.Log


class iGay69 : MainAPI() {
    override var mainUrl = "https://igay69.com"
    override var name = "iGay69"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded


    override val mainPage = mainPageOf(
        ""                         to "Latest",
        "category/porn/gaydar-porn"                    to "Gaydar",
        "category/leak/march-cmu"                                to "March CMU",
        "araw-2025"                             to "Araw",
    )    

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val pageUrl = if (page == 1) 
        "$mainUrl/${request.data}" 
    else 
        "$mainUrl/${request.data}/page/$page"

    val document = app.get(pageUrl).document
    val home = document.select("article.blog-entry").mapNotNull { it.toSearchResult() }

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
    val title = this.select("h2.wpex-card-title a").text()
    val href = fixUrl(this.select("h2.wpex-card-title a").attr("href"))
    val posterUrl = fixUrlNull(this.select("div.wpex-card-thumbnail img").attr("src"))

    return newMovieSearchResponse(title, href, TvType.NSFW) {
        this.posterUrl = posterUrl
    }
}

private fun Element.toRecommendResult(): SearchResponse? {
    val title = this.select("h2.wpex-card-title a").text()
    val href = fixUrl(this.select("h2.wpex-card-title a").attr("href"))
    val posterUrl = fixUrlNull(this.select("div.wpex-card-thumbnail img").attr("src"))
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

override suspend fun search(query: String): List<SearchResponse> {
    val searchResponse = mutableListOf<SearchResponse>()

    for (i in 1..7) {
        val document = app.get("$mainUrl/?s=$query&page=$i").document
        val results = document.select("article.blog-entry").mapNotNull { it.toSearchResult() }

        if (results.isEmpty()) break
        searchResponse.addAll(results)
    }

    return searchResponse
}
   
override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document

    val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
        ?: document.selectFirst("h1.single-post-title")?.text()?.trim()
        ?: url

    val poster = fixUrlNull(
        document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: listOf("data-src", "data-lazy-src", "src")
                .mapNotNull { attr -> document.selectFirst("figure.wp-block-image img")?.absUrl(attr) }
                .firstOrNull { it.isNotBlank() }
    )

    val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        ?: document.selectFirst("div.single-blog-content")?.text()?.trim()

    val recommendations = document.select("div.list-item div.video.col-2")
        .mapNotNull { it.toRecommendResult() }

    // Lấy danh sách tập, loại trùng theo số tập
    val episodes = document.select("div.single-blog-content a[href]")
        .mapNotNull { a ->
            val href = a.attr("href").trim()
            val text = a.text().trim()
            val match = Regex("(?i)(part|tập)\\s*(\\d+)").find(text)
            val partNumber = match?.groupValues?.getOrNull(2)?.toIntOrNull()

            if (href.startsWith("http") && partNumber != null) {
                partNumber to href
            } else null
        }
        .distinctBy { it.first } // chỉ giữ mỗi số tập 1 lần
        .sortedBy { it.first }
        .map { (partNumber, href) ->
            newEpisode(href) {
                this.name = "Tập $partNumber"
            }
        }

    return if (episodes.isNotEmpty()) {
        newTvSeriesLoadResponse(title, url, TvType.NSFW, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
        }
    } else {
        newMovieLoadResponse(title, url, TvType.NSFW, url) {
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
    val document = try {
        app.get(data).document
    } catch (e: Exception) {
        Log.e("igay69", "loadLinks: failed to fetch $data -> ${e.message}")
        return false
    }

    Log.d("igay69", "=== LOAD LINKS for: $data ===")
    Log.d("igay69", "Document title: ${document.selectFirst("title")?.text() ?: "no title"}")

    var found = false

    // 1) Nếu trang này có mirror menu giống trước -> lấy ra
    val mirrorUrls = document.select("ul#mirrorMenu a.mirror-opt, a.dropdown-item.mirror-opt")
        .mapNotNull { it.attr("data-url").takeIf { u -> u.isNotBlank() && u != "#" } }
        .toMutableSet()
    Log.d("igay69", "Mirrors found from data-url: ${mirrorUrls.joinToString()}")

    // 2) Nếu không có mirrors, thử iframe trực tiếp trong trang (nguồn embed)
    if (mirrorUrls.isEmpty()) {
        document.selectFirst("iframe[src], iframe[data-src]")?.let { iframe ->
            val src = iframe.absUrl("src").ifBlank { iframe.attr("data-src") }
            if (src.isNotBlank()) {
                mirrorUrls.add(src)
                Log.d("igay69", "Added iframe src as mirror: $src")
            }
        }
    }

    // 3) Nếu vẫn chưa có gì, và nếu đây là trang bài viết (có responsive-tabs / tabcontent),
    //    thu thập tất cả <a> trong các panels (linksv1, linksv2, ...)
    val tabAnchors = document.select(
        ".responsive-tabs__panel a[href], .responsive-tabs__panel a[href] *[href], " +
        ".tabcontent a[href], .tabcontent a[href] *[href], " +
        ".single-blog-content a[href]"
    )
    if (tabAnchors.isNotEmpty()) {
        tabAnchors.forEach { a ->
            // absUrl sẽ trả link đầy đủ nếu có, nếu không thì lấy attr("href")
            val href = a.absUrl("href").ifBlank { a.attr("href") }.trim()
            if (href.isNotBlank() && href != "#") mirrorUrls.add(href)
        }
        Log.d("igay69", "Anchors found in tabs/content: ${mirrorUrls.joinToString()}")
    }

    // 4) Nếu mirrorUrls rỗng, có thể data chính là một link host (ví dụ luluvdoo...), thêm chính nó
    if (mirrorUrls.isEmpty()) {
        Log.d("igay69", "No mirrors/iframe/tabs found; will try the page itself: $data")
        mirrorUrls.add(data)
    }

    // Chuẩn hóa và loại trùng
    val urls = mirrorUrls.map { raw ->
        // handle protocol-relative and whitespace
        raw.trim().let { u ->
            if (u.startsWith("//")) "https:$u" else u
        }
    }.filter { it.isNotBlank() }.toSet()

    // Helper: try loadExtractor trên từng url; nếu loadExtractor trả true -> đánh dấu found = true
    urls.forEach { url ->
        try {
            Log.d("igay69", "Trying loadExtractor for: $url (referer=$data)")
            val ok = loadExtractor(
                url,
                referer = data,
                subtitleCallback = subtitleCallback
            ) { link ->
                // Ghi log an toàn và forward link
                Log.d("igay69", "EXTRACTOR CALLBACK -> ${link.toString()}")
                callback(link)
            }
            Log.d("igay69", "loadExtractor returned $ok for $url")
            if (ok) found = true
        } catch (e: Exception) {
            Log.e("igay69", "Exception while loadExtractor($url): ${e.message}")
        }
    }

    // --- Extra: nếu muốn biết số phần (part) từ text của <a>, ta log ra giúp debug ---
    // (không ảnh hưởng tới logic chính; chỉ informational)
    try {
        document.select(".responsive-tabs__panel, .tabcontent, .single-blog-content").forEach { panel ->
            panel.select("a[href]").forEach { a ->
                val txt = a.text().trim()
                val partMatch = Regex("(?i)part\\s*0*([0-9]{1,3})|tập\\s*0*([0-9]{1,3})").find(txt)
                if (partMatch != null) {
                    val num = partMatch.groupValues.filter { it.isNotBlank() }.lastOrNull()
                    if (!num.isNullOrBlank()) {
                        Log.d("igay69", "Found link part#$num -> ${a.absUrl("href").ifBlank { a.attr("href") }} (text='$txt')")
                    }
                }
            }
        }
    } catch (e: Exception) {
        // ignore
    }

    Log.d("igay69", "=== finished loadLinks; found=$found ===")
    return found
}
}