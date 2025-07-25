package com.Fxggxt

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class DoodExtractor(
    override val name: String = "doodstream",
    override val mainUrl: String = "https://vide0.net/e/",
    override val requiresReferer: Boolean = false
) : ExtractorApi() {
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Chuyển đổi URL từ doodstream.com sang vide0.net
        val convertedUrl = url.replace(
            oldValue = "doodstream.com",
            newValue = "vide0.net",
            ignoreCase = true
        )
        
        val document = app.get(convertedUrl).document
        var found = false

        document.select("div.responsive-player iframe").forEach { iframe ->
            val src = iframe.attr("src")
            val videoHash = src.substringAfter("/")
            val directUrl = "$mainUrl$videoHash"
            
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    directUrl,
                    ExtractorLinkType.M3U8
                )
            )
            found = true
        }
        
        if (!found) {
            Log.warn("Không tìm thấy iframe phát video trong trang")
        }
    }
}
