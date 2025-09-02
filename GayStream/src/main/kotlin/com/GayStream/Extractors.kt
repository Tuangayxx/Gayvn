package com.GayStream

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.network.*
import com.lagradost.cloudstream3.*
import com.lagradost.api.Log
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import org.json.JSONArray

class ListMirror : ExtractorApi() {
    override val name = "ListMirror"
    override val mainUrl = "https://listmirror.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val links = mutableListOf<ExtractorLink>()
        val doc = app.get(url).document

        // Lấy script có chứa "sources = [ ... ];"
        val script = doc.select("script").mapNotNull { it.data() }
            .firstOrNull { it.contains("sources") }

        if (script != null) {
            val regex = Regex("""sources\s*=\s*(\[.*?]);""", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(script)
            val json = match?.groupValues?.get(1)

            if (json != null) {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val mirrorUrl = obj.optString("url")
                    if (mirrorUrl.isNotBlank()) {
                        loadExtractor(mirrorUrl, referer, { /* subs */ }) { link ->
                            links.add(link)
                        }
                    }
                }
            }
        }

        return links
    }
}


open class VoeExtractor : ExtractorApi() {
    override val name = "Voe"
    override val mainUrl = "https://jilliandescribecompany.com"
    override val requiresReferer = false

    private data class VideoSource(
        @JsonProperty("hls") val url: String?,
        @JsonProperty("video_height") val height: Int?
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val response = app.get(url)
        if (response.code == 404) return emptyList()

        val jsonMatch = Regex("""const\s+sources\s*=\s*(\{.*?\});""")
            .find(response.text)
            ?.groupValues?.get(1)
            ?.replace("0,", "0")
            ?: return emptyList()

        return tryParseJson<VideoSource>(jsonMatch)?.let { source ->
            source.url?.let { videoUrl ->
                listOf(
                    newExtractorLink(
                        name = name,
                        source = name,
                        url = videoUrl,
                        type = INFER_TYPE
                    )
                )
            } ?: emptyList()
        } ?: emptyList()
    }
}


open class dsio : ExtractorApi() {
    override val name = "dsio"
    override val mainUrl = "d-s.io"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
            val response0 = app.get(url).text

            val passMd5Path = Regex("/pass_md5/[^'\"]+").find(response0)?.value ?: return null
            val token = passMd5Path.substringAfterLast("/")
        
            val md5Url = mainUrl + passMd5Path
            val res = app.get(md5Url, referer = url) // Sử dụng URL gốc làm referer
            val videoData = res.text

            val randomStr = (1..10).map { 
            (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() 
                }.joinToString("")

            val link = "$videoData$randomStr?token=$token&expiry=${System.currentTimeMillis()}"

            val quality = Regex("(\\d{3,4})[pP]")
            .find(response0.substringAfter("<title>").substringBefore("</title>"))
            ?.groupValues?.get(1)

                return listOf(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = link,
                        type = INFER_TYPE
                                    ) {
                        this.referer = mainUrl
                        this.quality = getQualityFromName(quality)
            }
        )
    }
}

class DoodstreamCom : DoodLaExtractor() {
    override var mainUrl = "https://doodstream.com"
}

class vide0 : DoodLaExtractor() {
    override var mainUrl = "https://vide0.net"
}

class tapepops : StreamTape() {
    override var mainUrl = "https://tapepops.com"
    override var name = "tapepops"
}


class Bgwp : Bigwarp() {
    override var mainUrl = "https://bgwp.cc"
}

open class Bigwarp : ExtractorApi() {
    override var name = "Bigwarp"
    override var mainUrl = "https://bigwarp.io"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val link = app.get(url, allowRedirects = false).headers["location"] ?: url
        val source = app.get(link).document.selectFirst("body > script").toString()
        val regex = Regex("""file:\s*\"((?:https?://|//)[^\"]+)""")
        val matchResult = regex.find(source)
        val match = matchResult?.groupValues?.get(1)

        if (match != null) {
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = match
                ) {
                    this.referer = ""
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}


class Bigwarpcc : Bigwarp() {
    override var mainUrl = "https://bigwarp.cc"
}

class FileMoon : FilemoonV2() {
    override var mainUrl = "https://filemoon.to"
    override var name = "FileMoon"
}

class FileMoonSx : FilemoonV2() {
    override var mainUrl = "https://filemoon.sx"
    override var name = "FileMoonSx"
}


open class FilemoonV2 : ExtractorApi() {
    override var name = "Filemoon"
    override var mainUrl = "https://filemoon.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val defaultHeaders = mapOf(
            "Referer" to url,
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:137.0) Gecko/20100101 Firefox/137.0"
        )

        val initialResponse = app.get(url, defaultHeaders)
        val iframeSrcUrl = initialResponse.document.selectFirst("iframe")?.attr("src")

        if (iframeSrcUrl.isNullOrEmpty()) {
            val fallbackScriptData = initialResponse.document
                .selectFirst("script:containsData(function(p,a,c,k,e,d))")
                ?.data().orEmpty()

            val unpackedScript = JsUnpacker(fallbackScriptData).unpack()
            val videoUrl = unpackedScript?.let {
                Regex("""sources:\[\{file:"(.*?)"""").find(it)?.groupValues?.get(1)
            }

            if (!videoUrl.isNullOrEmpty()) {
                M3u8Helper.generateM3u8(
                    name,
                    videoUrl,
                    mainUrl,
                    headers = defaultHeaders
                ).forEach(callback)
            } else {
                Log.d("FilemoonV2", "No iframe and no video URL found in script fallback.")
            }
            return
        }

        // If iframe was found, continue processing
        val iframeHeaders = defaultHeaders + ("Accept-Language" to "en-US,en;q=0.5")
        val iframeResponse = app.get(iframeSrcUrl, headers = iframeHeaders)

        val iframeScriptData = iframeResponse.document
            .selectFirst("script:containsData(function(p,a,c,k,e,d))")
            ?.data().orEmpty()

        val unpackedScript = JsUnpacker(iframeScriptData).unpack()
        val videoUrl = unpackedScript?.let {
            Regex("""sources:\[\{file:"(.*?)"""").find(it)?.groupValues?.get(1)
        }

        if (!videoUrl.isNullOrEmpty()) {
            M3u8Helper.generateM3u8(
                name,
                videoUrl,
                mainUrl,
                headers = defaultHeaders
            )
        } else {
            // Last-resort fallback using WebView interception
            val resolver = WebViewResolver(
                interceptUrl = Regex("""(m3u8|master\.txt)"""),
                additionalUrls = listOf(Regex("""(m3u8|master\.txt)""")),
                useOkhttp = false,
                timeout = 15_000L
            )

            val interceptedUrl = app.get(
                iframeSrcUrl,
                referer = referer,
                interceptor = resolver
            ).url

            if (interceptedUrl.isNotEmpty()) {
                M3u8Helper.generateM3u8(
                    name,
                    interceptedUrl,
                    mainUrl,
                    headers = defaultHeaders
                )
            } else {
                Log.d("FilemoonV2", "No video URL intercepted in WebView fallback.")
            }
        }
    }
}