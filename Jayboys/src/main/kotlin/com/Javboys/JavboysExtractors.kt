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
import com.fasterxml.jackson.annotation.JsonProperty

abstract class BaseVideoExtractor : ExtractorApi() {
    protected abstract val domain: String
    override val mainUrl: String get() = "https://$domain"
}

open class 1069website  : ExtractorApi() {
    override val name = "1069website"
    override val mainUrl = "https://1069.website"
    override val requiresReferer = false 
    
    
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {
            this.document.let { document ->
                val link = document.select("div.download-button-wrapper").attr("href")
                val videohash = link.substringAfterLast("/")
                val finalLink = "https://l455o.com/bkg/$videohash"
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
        return null
    }
    }



abstract class BaseVideoExtractor : ExtractorApi() {
    protected abstract val domain: String
    override val mainUrl: String get() = "https://$domain"
}

    class VoeExtractor : BaseVideoExtractor() {
    override val name = "Voe"
    override val domain = "jilliandescribecompany.com"
    override val mainUrl = "https://$domain"
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
