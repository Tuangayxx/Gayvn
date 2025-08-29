package com.Nurgay

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

        val finalLink = document.select("ul.dropdown-menu a").attr("data-url")
        if (finalLink.isBlank()) return null

        return listOf(
            newExtractorLink(
                source = name,
                name = name,
                url = finalLink,
                type = INFER_TYPE
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
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


open class dsio : ExtractorApi() {
    override val name = "dsio"
    override val mainUrl = "d-s.io"
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

class DoodstreamCom : DoodLaExtractor() {
    override var mainUrl = "https://doodstream.com"
}

class vide0 : DoodLaExtractor() {
    override var mainUrl = "https://vide0.net"
}

class tapepops : StreamTape() {
    override var mainUrl = "https://tapepops.com"
    override var name = "tapepops"
}

class FileMoon : FilemoonV2() {
    override var mainUrl = "https://filemoon.to"
    override var name = "FileMoon"
}

open class Bigwarp : ExtractorApi() {
    override val name = "Bigwarp"
    override val mainUrl = "https://bigwarp.io"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<ExtractorLink>? {
        val response =app.get(url).document
        val script = response.selectFirst("script:containsData(sources)")?.data().toString()
        Regex("sources:\\s*\\[.file:\"(.*)\".*").find(script)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = link,
                        INFER_TYPE
                    ) {
                        this.referer = referer ?: "$mainUrl/"
                        this.quality = Qualities.P1080.value
                    }
                )
        }
        return null
    }
}

class Bigwarpcc : Bigwarp() {
    override var mainUrl = "https://bigwarp.cc"
}

class listmirror : Bigwarp() {
    override var mainUrl = "https://listmirror.com"    
}