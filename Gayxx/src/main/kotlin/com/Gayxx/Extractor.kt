package com.Gayxx

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.nodes.Document
import com.Gayxx.Gayxx

abstract class BaseVideoExtractor : ExtractorApi() {
    protected abstract val domain: String
    override val mainUrl: String get() = "https://$domain"
    
    protected suspend fun newHlsLink(
        url: String,
        name: String = this.name
    ) = newExtractorLink(
        name = name,
        source = this.name,
        url = url,
        type = INFER_TYPE
     
    )
}

class Stream : BaseVideoExtractor() {
    override val name = "Stream"
    override val domain = "vide0.net"
    override val mainUrl = "https://$domain/e"
    override val requiresReferer = false
}

class VoeExtractor : BaseVideoExtractor() {
    override val name = "Voe"
    override val domain = "voe.sx"
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
            source.url?.let { url ->
                listOf(newHlsLink(url))
            } ?: emptyList()
        } ?: emptyList()
    }
}

class Vide0Extractor : BaseVideoExtractor() {
    override val name = "Vide0"
    override val domain = "vide0.net"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document
        
        doc.select("#video-code iframe[src]").mapNotNull { iframe ->
            val src = iframe.attr("src")
                if (src.isNotBlank() && src.contains("/")) {
                    src.substringAfterLast("/")
    }              else null
        }.forEach { videoId ->
            callback(newHlsLink("$mainUrl/e/$videoId"))
        }
    }
}

class DoodstreamExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://DoodStream.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // html of DoodStream page to look for /pass_md5/...
        val response0 = app.get(url).text

        // get https://dood.ws/pass_md5/...
        val md5 = mainUrl + (Regex("/pass_md5/[^']*").find(response0)?.value ?: return null)
        val res = app.get(md5, referer = mainUrl + "/e/" + url.substringAfterLast("/"))

        // (zUEJeL3mUN is random)
        val trueUrl =
            if (res.toString().contains("cloudflarestorage")) res.toString()
            else res.text + "zUEJeL3mUN?token=" + md5.substringAfterLast("/")

        val quality =
            Regex("\\d{3,4}p")
                .find(response0.substringAfter("<title>").substringBefore("</title>"))
                ?.groupValues
                ?.get(0)

        return listOf(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = trueUrl
            ) {
                this.referer = mainUrl
                this.quality = getQualityFromName(quality)
            }
        ) // links are valid for 8h
    }
}