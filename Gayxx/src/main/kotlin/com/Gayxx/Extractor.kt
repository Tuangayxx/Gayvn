package com.Gayxx

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject


class Stream : Filesim() {
    override var mainUrl = "https://vide0.net/e"
}


open class VoeExtractor : ExtractorApi() {
    override var name: String = "Voe"
    override val mainUrl: String = "https://voe.sx"
    override val requiresReferer = false

    private data class ResponseLinks(
        @JsonProperty("hls") val url: String?,
        @JsonProperty("video_height") val label: Int?
        //val type: String // Mp4
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        val doc = app.get(url).text
        if (doc.contains("Error 404 - File not found")) return null
        if (doc.isNotBlank()) {
            val start = "const sources ="
            var src = doc.substring(doc.indexOf(start))
            src = src.substring(start.length, src.indexOf(";"))
                .replace("0,", "0")
                .trim()
            //Log.i(this.name, "Result => (src) ${src}")
            tryParseJson<ResponseLinks?>(src)?.let { voelink ->
                //Log.i(this.name, "Result => (voelink) ${voelink}")
                val linkUrl = voelink.url
                val linkLabel = voelink.label?.toString() ?: ""
                if (!linkUrl.isNullOrEmpty()) {
                    extractedLinksList.add(
                        newExtractorLink(
                            name = "Voe",
                            source = this.name,
                            url = linkUrl
                        )
                    )
                }
            }
        }
        return extractedLinksList
    }
}


class vide0Extractor(
    override val name: String = "vide0",
    override val mainUrl: String = "https://vide0.net/e/",
    override val requiresReferer: Boolean = false
) : ExtractorApi() {
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        var found = false

        document.select("#video-code iframe").forEach { iframe ->
            val src = iframe.attr("src")
            val videoHash = src.substringAfter("/")
            val directUrl = "$mainUrl/$videoHash"
        callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    directUrl,
                    ExtractorLinkType.M3U8
                )
            )
        }
    }
}

