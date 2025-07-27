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
        val document = app.get(url).document
        var found = false

        document.select("div.responsive-player iframe").forEach { iframe ->
            val src = iframe.attr("src")
            val videoHash = src.substringAfter("/")
            val directUrl = "http://vvide0.net/e/$videoHash"
            
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    directUrl
                )
            )
        }
    }
}

open class vide0Extractor : ExtractorApi() {
    override var name = "vide0"
    override var mainUrl = "https://vide0.net"
    override val requiresReferer = true // Bật yêu cầu referer

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response0 = app.get(url, referer = referer).text
        
        // Tìm đường dẫn pass_md5
        val passMd5Path = Regex("/pass_md5/[^'\"]+").find(response0)?.value ?: return null
        val token = passMd5Path.substringAfterLast("/")
        
        // Lấy dữ liệu video
        val md5Url = mainUrl + passMd5Path
        val res = app.get(md5Url, referer = url) // Sử dụng URL gốc làm referer
        val videoData = res.text
        
        // Tạo chuỗi ngẫu nhiên chính xác
        val randomStr = (1..10).map { 
            (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() 
        }.joinToString("")
        
        // Tạo URL hoàn chỉnh
        val trueUrl = "$videoData$randomStr?token=$token&expiry=${System.currentTimeMillis()}"
        
        // Lấy chất lượng video (cải tiến regex)
        val quality = Regex("(\\d{3,4})[pP]")
            .find(response0.substringAfter("<title>").substringBefore("</title>"))
            ?.groupValues?.get(1)

        return listOf(
            newExtractorLink(
                source = name,
                name = name,
                url = trueUrl,
                type = INFER_TYPE
            ) {
                this.referer = mainUrl
                this.quality = getQualityFromName(quality)
            }
        )
    }
}
