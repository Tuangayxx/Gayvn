package com.Jayboys

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.extractors.*
import com.fasterxml.jackson.annotation.JsonProperty


open class yi069website : ExtractorApi() {
    override val name = "yi069website"
    override val mainUrl = "https://1069.website"
    override val requiresReferer = false 

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url)
        val document = response.document

        // 1. Chọn chính xác thẻ <a> đầu tiên trong download-button-wrapper
        val firstDownloadLink = document.select("div.download-button-wrapper a:first-child").attr("href")
        if (firstDownloadLink.isBlank()) return null

        // 2. Trích xuất ID video (phần cuối sau dấu '/')
        val videoId = firstDownloadLink.substringAfterLast("/")
        if (videoId.isBlank()) return null

        // 3. Sửa domain chính xác: 1455o thay vì l455o
        var domain = "https://domain.com/bkg"
        val finalLink = "$domain/$videoId"

        return listOf(
            newExtractorLink(
                source = name,
                name = "yi069video",
                url = finalLink,
                type = INFER_TYPE
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

class Voe : Voe() {
    override var mainUrl = "https://voe.sx"
}

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


class dsio : DoodLaExtractor() {
    override var mainUrl = "https://d-s.io"
}

class DoodstreamCom : DoodLaExtractor() {
    override var mainUrl = "https://doodstream.com"
}

class vide0 : DoodLaExtractor() {
    override var mainUrl = "https://vide0.net"
}

class doodli : DoodLaExtractor() {
    override var mainUrl = "https://dood.li"
}

class tapepops : StreamTape() {
    override var mainUrl = "https://tapepops.com"
    override var name = "tapepops"
}

class FileMoon : FilemoonV2() {
    override var mainUrl = "https://filemoon.to"
    override var name = "FileMoon"
}

open class Base64Extractor : ExtractorApi() {
    override val name = "Base64"
    override val mainUrl = "base64"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // Nếu chính là data-uri thì trả luôn
        if (url.startsWith("data:video/")) {
            return listOf(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = url,
                    type = INFER_TYPE
                ) {
                    this.referer = referer ?: mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }

        // Nếu là trang HTML -> lấy response text và tìm data-uri bằng regex
        val responseText = app.get(url).text
        val regex = Regex("""data:video\/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/=]+""")
        val match = regex.find(responseText) ?: return null
        val dataUri = match.value // chính là "data:video/..;base64,AAAA..."

        return listOf(
            newExtractorLink(
                source = name,
                name = name,
                url = dataUri,
                type = INFER_TYPE
            ) {
                this.referer = referer ?: mainUrl
                this.quality = Qualities.Unknown.value
            }
        )
    }
}


class JP : Base64Extractor() {
    override var mainUrl = "https://1069jp.com"
    override var name = "JP"
}