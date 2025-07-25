package com.Fxggxt

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app

class DoodExtractor : ExtractorApi() {
    override val name = "doodstream"
    override val mainUrl = "https://vide0.net"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Chuyển đổi domain từ doodstream.com sang vide0.net
        val convertedUrl = url.replace(
            oldValue = "doodstream.com",
            newValue = "vide0.net",
            ignoreCase = true
        )
        
        val document = app.get(convertedUrl).document

        document.select("div.responsive-player iframe").forEach { iframe ->
            val src = iframe.attr("src")
            
            // Xử lý cả URL tương đối và tuyệt đối
            val videoPath = when {
                src.startsWith("http") -> src.substringAfter("//").substringAfter("/")
                src.startsWith("//") -> src.substringAfter("//").substringAfter("/")
                else -> src
            }
            
            // Tạo URL stream cuối cùng
            val streamUrl = "$mainUrl/e/$videoPath"
            
            callback(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = streamUrl,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8
                )
            )
        }
    }
}
