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

    // --- 1) Thu thập tất cả URL có ích ---
    val anchorSelectors = listOf(
        ".responsive-tabs__panel a[href]",
        ".tabcontent a[href]",
        ".single-blog-content a[href]",
        ".list-item a[href]",
        "ul#mirrorMenu a.mirror-opt",
        "a.dropdown-item.mirror-opt",
        "iframe[src]"
    )

    val rawUrls = linkedSetOf<String>()
    anchorSelectors.forEach { sel ->
        document.select(sel).forEach { el ->
            val href = when {
                el.tagName() == "iframe" -> el.absUrl("src").ifBlank { el.attr("data-src") }
                else -> el.absUrl("href").ifBlank { el.attr("href") }
            }?.trim() ?: ""
            if (href.isNotBlank() && href != "#" && !href.startsWith("javascript:")) {
                // normalize protocol-relative
                val norm = if (href.startsWith("//")) "https:$href" else href
                rawUrls.add(norm)
            }
        }
    }

    Log.d("igay69", "rawUrls collected: ${rawUrls.joinToString().take(2000)}")

    // --- 2) Nhóm theo số tập (nếu có) dựa vào text của <a> hoặc filename ---
    val partMap = mutableMapOf<Int, MutableSet<String>>() // part -> set(url)
    rawUrls.forEach { url ->
        // Try to find a nearby anchor text that mentions part/tập
        val anchors = document.select("a[href]").filter { a ->
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            href.isNotBlank() && (href == url || url.contains(href) || href.contains(url))
        }
        var partNum: Int? = null
        if (anchors.isNotEmpty()) {
            anchors.forEach { a ->
                val t = a.text().trim()
                val m = Regex("(?i)\\b(?:part|tập)\\s*0*([0-9]{1,3})\\b").find(t)
                if (m != null) {
                    partNum = m.groupValues.getOrNull(1)?.toIntOrNull()
                }
            }
        }
        // fallback: try find "partX" in url path
        if (partNum == null) {
            val m2 = Regex("(?i)(?:part|p|ep|episode)[-_\\. ]*0*([0-9]{1,3})").find(url)
            partNum = m2?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        if (partNum != null) {
            partMap.getOrPut(partNum) { linkedSetOf() }.add(url)
        } else {
            // no part number -> put under 0 (general)
            partMap.getOrPut(0) { linkedSetOf() }.add(url)
        }
    }

    Log.d("igay69", "partMap keys: ${partMap.keys}")

    // --- 3) Nếu đây là 1 trang series (nhiều part) và data không phải link host, cố gắng xử lý ---
    val isIgayPage = data.contains("igay69.com") || data.contains("iGay69")
    val multipleParts = partMap.keys.size > 1 || (partMap.keys.size == 1 && partMap.keys.first() > 0)

    // Try to extract requested part from 'data' url (fragment or query)
    val requestedPart: Int? = run {
        val fragMatch = Regex("#(?:part|ep|tập)0*([0-9]{1,3})", RegexOption.IGNORE_CASE).find(data)
            ?: Regex("[?&](?:part|ep|tập)=0*([0-9]{1,3})", RegexOption.IGNORE_CASE).find(data)
        fragMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    // decide which urls to try
    val urlsToTry = mutableSetOf<String>()
    if (isIgayPage && multipleParts) {
        if (requestedPart != null && partMap.containsKey(requestedPart)) {
            urlsToTry.addAll(partMap[requestedPart]!!)
            Log.d("igay69", "Detected requestedPart=$requestedPart; trying only its URLs")
        } else {
            // If CloudStream called loadLinks with series page (no requested part),
            // we try to attempt all parts (so extractor can return playable streams for selected episode).
            Log.d("igay69", "Series page detected; no specific part requested -> trying all part URLs")
            partMap.values.forEach { urlsToTry.addAll(it) }
        }
    } else {
        // not a series page (or no parts detected) -> try all rawUrls
        urlsToTry.addAll(rawUrls)
    }

    // Fallback: if nothing collected, try the page itself
    if (urlsToTry.isEmpty()) {
        urlsToTry.add(data)
    }

    Log.d("igay69", "urlsToTry count=${urlsToTry.size}")

    // --- 4) Thử loadExtractor trên từng URL; nếu không được, fetch và dò mp4/m3u8 từ HTML ---
    val videoUrlRegex = Regex("""(https?:\/\/[^\s"'<>]+?\.(?:m3u8|mp4)(?:\?[^\s"'<>]*)?)""", RegexOption.IGNORE_CASE)
    for (url in urlsToTry) {
        try {
            Log.d("igay69", "Trying loadExtractor for: $url (referer=$data)")
            val ok = loadExtractor(
                url,
                referer = data,
                subtitleCallback = subtitleCallback
            ) { link ->
                Log.d("igay69", "EXTRACTOR CALLBACK -> ${link.toString()}")
                callback(link)
            }
            Log.d("igay69", "loadExtractor returned $ok for $url")
            if (ok) found = true

            // if loadExtractor failed to produce links, attempt direct parse of the host page for mp4/m3u8
            if (!ok) {
                try {
                    Log.d("igay69", "loadExtractor returned false -> fetching $url to search mp4/m3u8")
                    val hostDoc = try { app.get(url, headers = mapOf("Referer" to data)).document } catch (e: Exception) { null }
                    val hostHtml = hostDoc?.html() ?: app.get(url).body?.string() ?: ""
                    // search for direct video urls
                    val matches = videoUrlRegex.findAll(hostHtml).map { it.groupValues[1] }.toList()
                    if (matches.isNotEmpty()) {
                        matches.distinct().forEach { v ->
                            Log.d("igay69", "Found direct video URL fallback -> $v")
                            // forward to callback via loadExtractor (prefer), else try to create a minimal ExtractorLink
                            val tryOk = loadExtractor(v, referer = url, subtitleCallback = subtitleCallback) { link ->
                                Log.d("igay69", "EXTRACTOR CALLBACK (fallback) -> ${link.toString()}")
                                callback(link)
                            }
                            if (tryOk) found = true
                        }
                    } else {
                        Log.d("igay69", "No direct mp4/m3u8 found in host page for $url")
                    }
                } catch (ee: Exception) {
                    Log.e("igay69", "Error when fallback parsing $url -> ${ee.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("igay69", "Exception while loadExtractor($url): ${e.message}")
        }
    }

    Log.d("igay69", "=== finished loadLinks; found=$found ===")
    return found
}
}