package com.GXtapes

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
    override var mainUrl = "https://88z.io"
}


open class VID : ExtractorApi() {
    override var name = "VID Xtapes"
    override var mainUrl = "https://vid.xtapes.in"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url).document.toString()
        val link = response.substringAfter("src: '").substringBefore("',")
        return listOf(
            newExtractorLink(
                this.name,
                this.name,
                link,
                type = INFER_TYPE
            )
            {
                this.referer = referer ?: ""
            }
        )
    }
}

class GXtapesExtractor(
    override val name: String = "GXtapes",
    override val mainUrl: String = "https://74k.io/e/",
    override val requiresReferer: Boolean = false
) : ExtractorApi() {
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url).document
        val script = response.select("script").find { it.data().contains("eval") }?.data() ?: return
        val unpackedScript = getAndUnpack(script)
        val links = "{" + unpackedScript.substringAfter("var links={").substringBefore("};") + "}"
        val obj = JSONObject(links)
        obj.keys().forEach {
            var finalUrl = obj.getString(it)
            if (!finalUrl.startsWith("http")) finalUrl = fixUrl(finalUrl)
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    finalUrl,
                    ExtractorLinkType.M3U8
                )
            )
        }
    }
}

class GXtapesnewExtractor(
    override val name: String = "88z.io",
    override val mainUrl: String = "https://88z.io/",
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

        document.select("#viodeo-code iframe").forEach { iframe ->
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

