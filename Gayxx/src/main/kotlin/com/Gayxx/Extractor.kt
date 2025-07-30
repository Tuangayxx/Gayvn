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


class dsio : BaseVideoExtractor() {
    override val name = "dsio"
    override val domain = "d-s.io"
    override val mainUrl = "https://$domain"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
            val response0 = app.get(url).text

            val passMd5Path = Regex("/pass_md5/[^'\"]+").find(response0)?.value ?: return null
            val token = passMd5Path.substringAfterLast("/")
        
            val md5Url = mainUrl + passMd5Path
            val res = app.get(md5Url, referer = url) // Sử dụng URL gốc làm referer
            val videoData = res.text

            val randomStr = (1..10).map { 
            (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() 
                }.joinToString("")

            val link = "$videoData$randomStr?token=$token&expiry=${System.currentTimeMillis()}"

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


class HdgayPlayer : BaseVideoExtractor() {
    override val name = "HdgayPlayer"
    override val domain = "player.hdgay.net"
    override val mainUrl = "https://$domain"
    override val requiresReferer = true

    private data class VideoSource(
        @JsonProperty("file") val url: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("label") val qualityLabel: String?
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(
            url = url,
            referer = referer ?: "https://gayxx.net/",
            headers = mapOf("User-Agent" to "Mozilla/5.0")
        )

        if (!response.isSuccessful) return emptyList()

        // Tìm kiếm nguồn video trong script
        val scriptMatch = Regex("""sources:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .find(response.text ?: "")
            ?.groupValues?.get(1)
            ?: return emptyList()

        // Xử lý dữ liệu JSON không chuẩn
        val normalizedJson = scriptMatch
            .replace("'", "\"")
            .replace(Regex("""\s*(\w+)\s*:""")) { "\"${it.groupValues[1]}\":" }

        return tryParseJson<List<VideoSource>>("[$normalizedJson]")?.mapNotNull { source ->
            source.url.takeIf { it.isNotBlank() }?.let { videoUrl ->
                val quality = when {
                    source.qualityLabel != null -> source.qualityLabel.removeSuffix("p").toIntOrNull() ?: 720
                    else -> getQualityFromName(videoUrl)
                }
                newExtractorLink(
                    name = name,
                    source = name,
                    url = videoUrl,
                    type = INFER_TYPE
                ) {
                    this.headers = mapOf("Referer" to url)
                    this.quality = quality
                }
            }
        } ?: emptyList()
    }
}