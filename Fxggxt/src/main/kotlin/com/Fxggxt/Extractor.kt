package com.Fxggxt

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

open class DoodExtractor(
    override val name: String = "doodstream",
    override val mainUrl: String = "https://doodstream.com",
    override val requiresReferer: Boolean = false
) : ExtractorApi() {
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(convertedUrl).document
        var found = false

        document.select("div.responsive-player iframe").forEach { iframe ->
            val src = iframe.attr("src")
            val videoHash = src.substringAfter("/")
            val directUrl = "http://vide0.net/e/$videoHash"
            
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
