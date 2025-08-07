package com.Fxggxt

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.extractors.StreamTape
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import org.jsoup.Jsoup
import kotlinx.coroutines.delay
import java.util.Base64
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import android.annotation.SuppressLint


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
    override val mainUrl = "https://d-s.io" // SỬA: Dùng domain thực tế
    private val originUrl = "https://doodstream.com" // Domain gốc để tham chiếu
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        // SỬA: Thay thế domain trong response
        val response = app.get(url).text
        val normalizedResponse = response.replace("doodstream.com", "d-s.io")
        
        // Tìm pass_md5 path (sử dụng response đã chuẩn hóa)
        val passMd5Path = Regex("/pass_md5/[^'\"?]+").find(normalizedResponse)?.value 
            ?: throw Exception("No pass_md5 found")

        val token = passMd5Path.substringAfterLast("/")
        val md5Url = "$mainUrl$passMd5Path" // SỬA: Dùng mainUrl mới
        
        // SỬA: Referer phải là domain thực tế (d-s.io)
        val videoData = app.get(md5Url, referer = mainUrl).text
        
        // Tạo expiry timestamp (6 giờ - đơn vị GIÂY)
        val expiry = (System.currentTimeMillis() / 1000) + 21600 
        
        // SỬA: Cấu trúc URL chuẩn
        val videoUrl = "$videoData?token=$token&expiry=$expiry"
        
        // Lấy chất lượng từ response gốc
        val quality = Regex("""(\d{3,4})[pP][^0-9]""").find(response)?.groupValues?.get(1)?.toIntOrNull()
            ?: Qualities.Unknown.value

        return listOf(
            newExtractorLink(
                source = name,
                name = "Doodstream ${quality}p",
                url = videoUrl,
                type = INFER_TYPE
            ) {
                referer = mainUrl // SỬA: Referer domain thực
                quality = quality
            }
        )
    }
}


open class vvide0Extractor : ExtractorApi() {
        override var name = "vvide0"
        override var mainUrl = "https://vvide0.com"
        override val requiresReferer = true // Bật yêu cầu referer

        override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
            val response0 = app.get(url).text

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
        val link = "$videoData$randomStr?token=$token&expiry=${System.currentTimeMillis()}"
        
        // Lấy chất lượng video (cải tiến regex)
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


 open class HdgayPlayer : ExtractorApi() {
    override val name = "HdgayPlayer"
    override val mainUrl = "https://player.hdgay.net"
    override val requiresReferer = false

     private data class VideoSource(
        @JsonProperty("file") val file: String?,
        @JsonProperty("hls") val hls: String?,
        @JsonProperty("url") val url: String?,
        @JsonProperty("video_height") val height: Int?
    ) {
        fun getVideoUrl(): String? {
            return hls ?: file ?: url
        }
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val response = app.get(url)
        if (response.code == 404) return emptyList()

        // Cách 1: Tìm link m3u8 trong response (regex cải tiến)
        val m3u8Regex = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""")
        val m3u8Matches = m3u8Regex.findAll(response.text)
        m3u8Matches.forEach { match ->
            val m3u8Link = match.value
            return listOf(
                newExtractorLink(
                    name = name,
                    source = name,
                    url = m3u8Link,
                    type = INFER_TYPE
                )
            )
        }

        val sources = listOf(
            // Mẫu 1: const sources = {...};
            Regex("""const\s+sources\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL),
            // Mẫu 2: playerInstance.setup({...})
            Regex("""playerInstance\.setup\(\s*(\{.*?\})\s*\)""", RegexOption.DOT_MATCHES_ALL),
            // Mẫu 3: JWPlayer setup
            Regex("""jwplayer\s*\(\s*['"]\w+['"]\s*\)\.setup\(\s*(\{.*?\})\s*\)""")
        )

        sources.forEach { regex ->
            val match = regex.find(response.text)
            match?.groupValues?.get(1)?.let { jsonStr ->
                tryParseJson<VideoSource>(fixJson(jsonStr))?.getVideoUrl()?.let { videoUrl ->
                    return listOf(
                        newExtractorLink(
                            name = name,
                            source = name,
                            url = videoUrl,
                            type = INFER_TYPE
                        )
                    )
                }
            }
        }


 val document = Jsoup.parse(response.text)
        document.select("script").forEach { script ->
            val scriptData = script.data()
            if (scriptData.contains("sources")) {
                val patterns = listOf(
                    Regex("""sources\s*:\s*\[\s*(\{.*?\})\s*\]""", RegexOption.DOT_MATCHES_ALL),
                    Regex("""sources\s*:\s*(\{.*?\})""", RegexOption.DOT_MATCHES_ALL)
                )
                
                patterns.forEach { pattern ->
                    pattern.find(scriptData)?.groupValues?.get(1)?.let { jsonStr ->
                        tryParseJson<VideoSource>(fixJson(jsonStr))?.getVideoUrl()?.let { videoUrl ->
                            return listOf(
                                newExtractorLink(
                                    name = name,
                                    source = name,
                                    url = videoUrl,
                                    type = INFER_TYPE
                                )
                            )
                        }
                    }
                }
            }
        }

        return emptyList()
    }

    // Hàm sửa lỗi JSON phổ biến
    private fun fixJson(json: String): String {
        return json
            .replace(Regex(""",\s*\}"""), "}")
            .replace(Regex(""",\s*\]"""), "]")
            .replace(Regex("""(\w+)\s*:\s*('[^']*')"""), "$1:$2")
            .replace(Regex("""(\w+)\s*:\s*("[^"]*")"""), "$1:$2")
    }
}