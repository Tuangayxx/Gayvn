package com.Gayxx

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


abstract class BaseVideoExtractor : ExtractorApi() {
    protected abstract val domain: String
    override val mainUrl: String get() = "https://$domain"
}

class VoeExtractor : BaseVideoExtractor() {
    override val name = "Voe"
    override val domain = "jilliandescribecompany.com"
    override val mainUrl = "https://$domain/e"
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


class dsExtractor : ExtractorApi() {
    override var name = "dsExtractor"
    override var mainUrl = "https://doodstream.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // Tạo URL proxy để tránh chặn
        val proxyUrl = url.replace("doodstream.com", "d-s.io")
        
        // Tải nội dung HTML với referer chính xác
        val response = app.get(proxyUrl, referer = referer ?: mainUrl).text

        // Trích xuất token từ URL pass_md5
        val tokenRegex = Regex("""/pass_md5/[^'"]+\?token=([\w]+)""")
        val token = tokenRegex.find(response)?.groupValues?.get(1) ?: return null

        // Trích xuất ID file để xây dựng URL pass_md5
        val fileIdRegex = Regex("""\.cookie\('file_id',\s*'(\d+)'\)""")
        val fileId = fileIdRegex.find(response)?.groupValues?.get(1) ?: return null

        // Xây dựng URL pass_md5 hoàn chỉnh
        val passMd5Url = "$mainUrl/pass_md5/$fileId?token=$token"
        
        // Lấy dữ liệu video chính
        val videoData = app.get(passMd5Url, referer = proxyUrl).text

        // Tạo URL video hoàn chỉnh với token và thời gian hết hạn
        val randomStr = (1..10).joinToString("") { 
            ('a'..'z').random().toString() 
        }
        val expiry = System.currentTimeMillis()
        val videoUrl = "$videoData$randomStr?token=$token&expiry=$expiry"

        // Trích xuất chất lượng video
        val quality = Regex("""Spain In The Ass 2.*?(\d{3,4})[pP]""")
            .find(response)?.groupValues?.get(1)?.toIntOrNull()

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