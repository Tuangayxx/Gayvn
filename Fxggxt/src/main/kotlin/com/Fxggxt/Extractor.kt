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
    override val mainUrl = "https://d-s.io"
    override val requiresReferer = false
    
    // Sử dụng User-Agent mới nhất của Chrome
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.142 Safari/537.36"
    
    private fun getHeaders(referer: String = mainUrl): Map<String, String> {
        return mapOf(
            "User-Agent" to userAgent,
            "Referer" to referer,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.5",
            "Connection" to "keep-alive"
        )
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // Bước 1: Lấy HTML của trang
        val html = try {
            app.get(url, headers = getHeaders()).text
        } catch (e: Exception) {
            return null
        }
        
        // Bước 2: Tìm pass_md5 endpoint
        val passMd5Path = Regex("""/pass_md5/([^'"]+)""").find(html)?.value ?: return null
        val token = passMd5Path.substringAfterLast("/")
        
        // Bước 3: Lấy dữ liệu video từ endpoint
        val md5Url = "https://d-s.io$passMd5Path"
        val videoData = try {
            app.get(md5Url, headers = getHeaders(url)).text
        } catch (e: Exception) {
            return null
        }
        
        // Bước 4: Tạo URL video hoàn chỉnh
        val expiry = (System.currentTimeMillis() / 1000) + 21600 // 6 giờ
        val videoUrl = "$videoData?token=$token&expiry=$expiry"
        
        // Bước 5: Lấy thông tin chất lượng
        val quality = Regex("""(\d{3,4})[pP]""").find(html)?.run {
            groupValues[1].toIntOrNull() ?: Qualities.Unknown.value
        } ?: Qualities.Unknown.value

        return listOf(
            newExtractorLink(
                source = name,
                name = "Doodstream ${quality}p",
                url = videoUrl,
                type = INFER_TYPE
            ) {
                this.referer = mainUrl // SỬA: Referer domain thực
                this.quality = quality
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