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
    val document = app.get(data).document
    var found = false
    val urls = linkedSetOf<String>()

    fun normalize(raw: String): String {
        val u = raw.trim()
        if (u.isEmpty()) return ""
        if (u.startsWith("javascript:", true) || u.startsWith("data:", true) || u.startsWith("about:", true)) return ""
        if (u.startsWith("//")) return "https:$u"
        if (u.startsWith("http://") || u.startsWith("https://")) return u
        // resolve relative using baseUri of document
        return try {
            val base = java.net.URL(document.baseUri())
            java.net.URL(base, u).toString()
        } catch (e: Exception) {
            u
        }
    }

    // build embed variants for known hosts
    fun buildCandidates(orig: String): List<String> {
        val n = normalize(orig)
        if (n.isBlank()) return emptyList()
        val candidates = mutableListOf<String>()
        candidates += n

        val host = try {
            java.net.URI(n).host?.lowercase()?.removePrefix("www.") ?: ""
        } catch (e: Exception) { "" }

        // helper to extract last path token or query id
        fun extractId(u: String): String {
            val uri = try { java.net.URI(u) } catch (e: Exception) { return "" }
            // check query parameters common names
            val q = uri.query ?: ""
            val byQuery = listOf("id", "v", "video", "file", "VID").firstOrNull { q.contains("$it=") }?.let {
                q.split("&").firstOrNull { p -> p.startsWith("$it=") }?.substringAfter("=")
            } ?: ""
            if (byQuery?.isNotBlank() == true) return byQuery
            val path = uri.path ?: ""
            val last = path.trimEnd('/').substringAfterLast('/')
            return last
        }

        val id = extractId(n)

        when {
            host.contains("voe") -> {
                if (id.isNotBlank()) {
                    candidates += "https://$host/e/$id"
                    candidates += "https://$host/embed/$id"
                }
                // sometimes /v/ or /f/ variants
                candidates += n.replace("/f/", "/e/")
            }
            host.contains("d-s.io") || host.contains("d-s") -> {
                if (id.isNotBlank()) {
                    candidates += "https://$host/e/$id"
                    candidates += "https://$host/embed/$id"
                }
            }
            host.contains("bigwarp") -> {
                if (id.isNotBlank()) {
                    candidates += "https://$host/e/$id"
                    candidates += "https://$host/embed/$id"
                }
            }
            host.contains("vinovo") || host.contains("vinov") -> {
                if (id.isNotBlank()) {
                    candidates += "https://$host/e/$id"
                    candidates += "https://$host/embed/$id"
                }
            }
            else -> {
                // keep original only
            }
        }

        // make unique and normalized
        return candidates.mapNotNull { normalize(it).takeIf { it.isNotBlank() } }.distinct()
    }

    // collect from iframes (common)
    document.select("iframe").forEach { f ->
        // try absUrl first then attributes
        val attrs = listOf("src", "data-src", "data-lazy-src", "data-embed", "srcdoc")
        for (a in attrs) {
            val v = when (a) {
                "src" -> f.absUrl("src").ifBlank { f.attr("src") }
                "data-src" -> f.absUrl("data-src").ifBlank { f.attr("data-src") }
                "data-lazy-src" -> f.absUrl("data-lazy-src").ifBlank { f.attr("data-lazy-src") }
                "data-embed" -> f.absUrl("data-embed").ifBlank { f.attr("data-embed") }
                "srcdoc" -> f.attr("srcdoc")
                else -> ""
            }
            if (v.isNotBlank()) {
                urls += v
            }
        }
    }

    // also check <video> <source> and anchors that likely point to players
    document.select("video source[src], video[src]").forEach { s ->
        s.attr("src").takeIf { it.isNotBlank() }?.let { urls += it }
    }
    document.select("a[href]").forEach { a ->
        val href = a.absUrl("href").ifBlank { a.attr("href") }
        if (href.isNotBlank() && (href.contains("voe") || href.contains("d-s.io") || href.contains("bigwarp") || href.contains("vinovo") || href.contains("player") || href.contains("embed") || href.contains("cdn"))) {
            urls += href
        }
    }

    // prefer processing links that contain our 4 hosts first
    val preferred = urls.filter { u ->
        val h = try { java.net.URI(normalize(u)).host?.lowercase() ?: "" } catch (e: Exception) { "" }
        listOf("voe", "d-s.io", "bigwarp", "vinovo").any { h.contains(it) }
    }
    val others = urls.filterNot { preferred.contains(it) }
    val ordered = (preferred + others).distinct()

    val seenCandidates = linkedSetOf<String>()
    for (u in ordered) {
        val candidates = buildCandidates(u)
        for (c in candidates) {
            if (seenCandidates.contains(c)) continue
            seenCandidates += c
            try {
                // nếu loadExtractor trả về Boolean thì dùng kết quả; nếu không, chỉ gọi và set found = true khi muốn
                val ok = loadExtractor(c, subtitleCallback, callback) // giả sử trả về Boolean
                if (ok) {
                    found = true
                    // nếu muốn dừng sớm sau link thành công cho host ưu tiên, uncomment break
                    // break
                }
            } catch (e: Exception) {
                // log nếu cần, nhưng không throw
            }
        }
        // nếu bạn muốn dừng khi đã tìm thấy 1 link thành công, bỏ comment bên dưới
        // if (found) break
    }

    return found
}

}
