package com.Gayxx

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.JsUnpacker
import org.jsoup.nodes.Document

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

class Vide0Extractor : BaseVideoExtractor() {
    override val name = "Vide0"
    override val domain = "vide0.net"
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

class DoodUrlExtractor : ExtractorApi() {
    override var name = "vide0.com"
    override var mainUrl = "https://cloudatacdn.com"
    override val requiresReferer = false


    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response0 = app.get(url).text
        // Tìm đường dẫn pass_md5 và token
        val passMd5Path = Regex("/pass_md5/[^']*").find(response0)?.value ?: return null
        val token = passMd5Path.substringAfterLast("/")
        
        // Lấy dữ liệu video từ API
        val md5Url = mainUrl + passMd5Path
        val res = app.get(md5Url, referer = mainUrl + url.substringAfterLast("/"))
        val videoData = res.text
        
        // Tạo URL hoàn chỉnh với token và tham số ngẫu nhiên
        val randomStr = List(10) { 
            ('a'..'z') + ('A'..'Z') + ('0'..'9')
        }.joinToString("")
        val trueUrl = "$videoData$randomStr?token=$token&expiry=${System.currentTimeMillis()}"

        // Lấy chất lượng video từ title
        val quality = Regex("\\d{3,4}p")
            .find(response0.substringAfter("<title>").substringBefore("</title>"))
            ?.value

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

class MixDropExtractor : ExtractorApi() {
    override var name = "MixDrop"
    override var mainUrl = "https://mixdrop.to"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url, referer = mainUrl).document
        val extractedpack = response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        val unpacked = JsUnpacker(extractedpack).unpack()
        unpacked?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = link,
                        type = INFER_TYPE
                    ) {
                        this.referer = referer ?: ""
                    }
                )
            }
        }
        return null
    }
}

class StreamTapeNet : StreamTapeExtractor() {
    override var mainUrl = "https://streamtape.net"
}

class StreamTapeXyz : StreamTapeExtractor() {
    override var mainUrl = "https://streamtape.xyz"
}

class ShaveTape : StreamTapeExtractor() {
    override var mainUrl = "https://shavetape.cash"
}

open class StreamTapeExtractor : ExtractorApi() {
    override var name = "StreamTape"
    override var mainUrl = "https://streamtape.com"
    override val requiresReferer = false

    private val linkRegex =
        Regex("""'robotlink'\)\.innerHTML = '(.+?)'\+ \('(.+?)'\)""")

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url)
        linkRegex.find(response.text)?.let {
            val extractedUrl =
                "https:${it.groups[1]!!.value + it.groups[2]!!.value.substring(3)}"
            return listOf(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = extractedUrl,
                    type = INFER_TYPE
                ) {
                    this.referer = referer ?: ""
                }
            )
        }
        return null
    }
}


class DoodExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://doodstream.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response0 = app.get(url).text
        // Tìm đường dẫn pass_md5 và token
        val passMd5Path = Regex("/pass_md5/[^']*").find(response0)?.value ?: return null
        val token = passMd5Path.substringAfterLast("/")
        
        // Lấy dữ liệu video từ API
        val md5Url = mainUrl + passMd5Path
        val res = app.get(md5Url, referer = mainUrl + url.substringAfterLast("/"))
        val videoData = res.text
        
        // Tạo URL hoàn chỉnh với token và tham số ngẫu nhiên
        val randomStr = List(10) { 
            ('a'..'z') + ('A'..'Z') + ('0'..'9')
        }.joinToString("")
        val trueUrl = "$videoData$randomStr?token=$token&expiry=${System.currentTimeMillis()}"

        // Lấy chất lượng video từ title
        val quality = Regex("\\d{3,4}p")
            .find(response0.substringAfter("<title>").substringBefore("</title>"))
            ?.value

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
