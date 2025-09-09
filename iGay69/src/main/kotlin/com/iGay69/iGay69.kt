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
        ""                                             to "Mới nhất",
        "category/porn/gaydar-porn"                    to "Gaydar",
        "category/leak"                                to "Leak",
        "category/leak/page/4"                         to "Leak4",
        "category/leak/page/7"                         to "Leak7",
        "category/leak/page/10"                        to "Leak10",
        "category/leak/page/13"                        to "Leak13",
        "category/leak/page/16"                        to "Leak16",
        "category/magazine"                            to "Magazine",
        "category/collection"                          to "Collection",
    )    

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val pageUrl = if (page == 1) 
        "$mainUrl/${request.data}" 
    else 
        "$mainUrl/${request.data}/page/$page/"

    val document = app.get(pageUrl).document
    val home = document.select("article.blog-entry").mapNotNull { it.toSearchResult() }
    val hasNext = document.selectFirst("a.next.page-numbers") != null

    return newHomePageResponse(
        list = HomePageList(
            name = request.name,
            list = home,
            isHorizontalImages = false
        ),
        hasNext = hasNext
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

    // robust poster extraction
val poster = run {
    val metaOg = document.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
    val linkImg = document.selectFirst("link[rel=image_src]")?.attr("href")?.takeIf { it.isNotBlank() }

    val imgSelectors = listOf(
        ".post-thumbnail img", ".wp-post-image", ".wp-block-image img",
        "figure img", ".entry-thumb img", ".single-post img", ".site-logo img"
    )
    val attrs = listOf("data-src", "data-lazy-src", "data-srcset", "src")

    val fromImgs = imgSelectors.asSequence().mapNotNull { sel ->
        document.selectFirst(sel)?.let { el ->
            if (el.tagName() == "meta") el.attr("content")
            else attrs.mapNotNull { a -> el.absUrl(a).takeIf { it.isNotBlank() } }.firstOrNull()
        }
    }.firstOrNull()

    val raw = metaOg ?: linkImg ?: fromImgs ?: ""
    fixUrlNull(raw)
}


    val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        ?: document.selectFirst("div.single-blog-content")?.text()?.trim()

    val recommendations = document.select("div.list-item div.video.col-2")
        .mapNotNull { it.toRecommendResult() }

    // Tìm tất cả <a> trong nội dung để lấy số tập (part/tập)
    val partPairs = document.select(".single-blog-content a[href], .responsive-tabs__panel a[href], .tabcontent a[href]")
        .mapNotNull { a ->
            val href = a.absUrl("href").ifBlank { a.attr("href") }.trim()
            val text = a.text().trim()
            val match = Regex("(?i)(?:part|tập)\\s*0*([0-9]{1,3})").find(text)
            val partNumber = match?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (href.startsWith("http") && partNumber != null) {
                partNumber to href
            } else null
        }
        .distinctBy { it.first } // chỉ giữ 1 per partNumber
        .sortedBy { it.first }

    // Tạo episodes trỏ về trang series + fragment #partN (điều này cho phép loadLinks() tìm 4 server cho mỗi tập)
    val episodes = partPairs.map { (partNumber, _) ->
        val episodeDataUrl = "$url#part$partNumber"
        newEpisode(episodeDataUrl) {
            this.name = "Tập $partNumber"
            this.posterUrl = poster
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
    Log.d("igay69", "=== loadLinks called with data=$data ===")
    var found = false

    // Detect series fragment: example "https://igay69.com/lion-s1-2025/#part3"
    val seriesMatch = Regex("^(https?://[^#]+)#part(\\d+)$", RegexOption.IGNORE_CASE).find(data)
    if (seriesMatch != null) {
        val seriesUrl = seriesMatch.groupValues[1]
        val partNumber = seriesMatch.groupValues[2].toIntOrNull() ?: 0
        Log.d("igay69", "Detected series page -> $seriesUrl ; requested part = $partNumber")

        val doc = try {
            app.get(seriesUrl).document
        } catch (e: Exception) {
            Log.e("igay69", "Failed to fetch series page $seriesUrl: ${e.message}")
            return false
        }

        // Chọn panels (linksv1..linksv4 tương ứng với panel thứ tự)
        val panels = doc.select(".responsive-tabs__panel, .tabcontent, .single-blog-content")
        if (panels.isEmpty()) {
            Log.d("igay69", "No panels found on series page.")
        }

        // Duyệt từng panel (mỗi panel thường là 1 server list)
        panels.forEachIndexed { panelIndex, panel ->
            try {
                val anchors = panel.select("a[href]")
                // Tìm anchor có text chứa "part N" hoặc "tập N"
                val partRegex = Regex("(?i)\\b(?:part|tập)\\s*0*${partNumber}\\b")
                val anchorMatch = anchors.firstOrNull { a -> partRegex.containsMatchIn(a.text()) }

                val chosenHref = when {
                    anchorMatch != null -> anchorMatch.absUrl("href").ifBlank { anchorMatch.attr("href") }
                    anchors.size >= partNumber && partNumber > 0 -> anchors[partNumber - 1].absUrl("href").ifBlank { anchors[partNumber - 1].attr("href") }
                    else -> null
                }

                if (!chosenHref.isNullOrBlank()) {
                    val norm = if (chosenHref.startsWith("//")) "https:$chosenHref" else chosenHref
                    Log.d("igay69", "Panel #${panelIndex + 1} -> found href for part $partNumber: $norm")
                    val ok = try {
                        loadExtractor(
                            norm,
                            referer = seriesUrl,
                            subtitleCallback = subtitleCallback
                        ) { link ->
                            Log.d("igay69", "EXTRACTOR CALLBACK -> ${link.toString()}")
                            callback(link)
                        }
                    } catch (e: Exception) {
                        Log.e("igay69", "loadExtractor exception for $norm: ${e.message}")
                        false
                    }
                    if (ok) found = true
                } else {
                    Log.d("igay69", "Panel #${panelIndex + 1} -> no href found for part $partNumber")
                }
            } catch (e: Exception) {
                Log.e("igay69", "Error processing panel #${panelIndex + 1}: ${e.message}")
            }
        }

        // Nếu panels không trả kết quả, thử bộ selector fallback (linksv1..4, #tablist1-panelX)
        if (!found) {
            val serverSelectors = listOf(
                ".linksv1", ".linksv2", ".linksv3", ".linksv4",
                "#tablist1-panel1", "#tablist1-panel2", "#tablist1-panel3", "#tablist1-panel4"
            )
            serverSelectors.forEach { sel ->
                if (found) return@forEach
                doc.select(sel).forEach { panel ->
                    val anchors = panel.select("a[href]")
                    val partRegex = Regex("(?i)\\b(?:part|tập)\\s*0*${partNumber}\\b")
                    val anchorMatch = anchors.firstOrNull { a -> partRegex.containsMatchIn(a.text()) }
                    val chosen = anchorMatch ?: anchors.getOrNull(partNumber - 1)
                    val href = chosen?.absUrl("href")?.ifBlank { chosen.attr("href") }
                    if (!href.isNullOrBlank()) {
                        val norm = if (href.startsWith("//")) "https:$href" else href
                        try {
                            val ok = loadExtractor(norm, referer = seriesUrl, subtitleCallback = subtitleCallback) { link ->
                                Log.d("igay69", "EXTRACTOR CALLBACK -> ${link.toString()}")
                                callback(link)
                            }
                            if (ok) found = true
                        } catch (e: Exception) {
                            Log.e("igay69", "Fallback loadExtractor exception for $norm: ${e.message}")
                        }
                    }
                }
            }
        }

        Log.d("igay69", "=== finished series loadLinks; found=$found ===")
        return found
    }

    // Nếu không phải series#part => coi như link host/episode trực tiếp
    try {
        Log.d("igay69", "Treating data as host URL -> trying loadExtractor for $data")
        val ok = loadExtractor(
            data,
            referer = null,
            subtitleCallback = subtitleCallback
        ) { link ->
            Log.d("igay69", "EXTRACTOR CALLBACK -> ${link.toString()}")
            callback(link)
        }
        if (ok) found = true
        else {
            // fallback: fetch host page và dò direct mp4/m3u8
            val pageHtml = try { app.get(data, headers = mapOf("Referer" to data)).body?.string() ?: "" } catch (e: Exception) { "" }
            val videoRegex = Regex("(https?://[^\\s'\"<>]+?\\.(?:m3u8|mp4)(?:\\?[^\\s'\"<>]*)?)", RegexOption.IGNORE_CASE)
            val matches = videoRegex.findAll(pageHtml).map { it.groupValues[1] }.distinct().toList()
            if (matches.isNotEmpty()) {
                matches.forEach { v ->
                    try {
                        val ok2 = loadExtractor(v, referer = data, subtitleCallback = subtitleCallback) { link ->
                            Log.d("igay69", "EXTRACTOR CALLBACK (fallback) -> ${link.toString()}")
                            callback(link)
                        }
                        if (ok2) found = true
                    } catch (e: Exception) {
                        Log.e("igay69", "Fallback loadExtractor($v) error: ${e.message}")
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("igay69", "Exception in host handling: ${e.message}")
    }

    Log.d("igay69", "=== finished loadLinks; found=$found ===")
    return found
}
}