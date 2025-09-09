package com.iGay69

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
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import org.json.JSONArray
import java.util.Base64
import android.util.Log
import android.annotation.SuppressLint
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable

class MxDrop : MixDrop(){
    override var mainUrl = "https://mxdrop.to"
}

class MixDropCv : MixDrop(){
    override var mainUrl = "https://mixdrop.cv"
}

class mdfx9dc8n : MixDrop(){
    override var mainUrl = "https://mdfx9dc8n.net"
}

class MixDropIs : MixDrop(){
    override var mainUrl = "https://mixdrop.is"
}

class luluvid : LuluStream(){
    override var mainUrl = "https://luluvid.com"
}

open class Lulustream : ExtractorApi() {
    override var name = "Lulustream"
    override var mainUrl = "https://lulustream.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url,referer=mainUrl).document
        val extractedpack =response.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        JsUnpacker(extractedpack).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = link,
                        INFER_TYPE
                    ) {
                        this.referer = referer ?: ""
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
        return null
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

class doodspro : DoodLaExtractor() {
    override var mainUrl = "https://doods.pro"
}

class doodyt : DoodLaExtractor() {
    override var mainUrl = "https://dood.yt"
}

class doodre : DoodLaExtractor() {
    override var mainUrl = "https://dood.re"
}

class doodpm : DoodLaExtractor() {
    override var mainUrl = "https://dood.pm"
}

class vide0 : DoodLaExtractor() {
    override var mainUrl = "https://vide0.net"
}

class tapepops : StreamTape() {
    override var mainUrl = "https://tapepops.com"
    override var name = "tapepops"
}

class watchadsontape : StreamTape() {
    override var mainUrl = "https://watchadsontape.com"
    override var name = "watchadsontape"
}

class FileMoon : FilemoonV2() {
    override var mainUrl = "https://filemoon.to"
    override var name = "FileMoon"
}

class Bgwp : Bigwarp() {
    override var mainUrl = "https://bgwp.cc"
}

open class Bigwarp : ExtractorApi() {
    override var name = "Bigwarp"
    override var mainUrl = "https://bigwarp.io"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val link = app.get(url, allowRedirects = false).headers["location"] ?: url
        val source = app.get(link).document.selectFirst("body > script").toString()
        val regex = Regex("""file:\s*\"((?:https?://|//)[^\"]+)""")
        val matchResult = regex.find(source)
        val match = matchResult?.groupValues?.get(1)

        if (match != null) {
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = match
                ) {
                    this.referer = ""
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}


class Bigwarpcc : Bigwarp() {
    override var mainUrl = "https://bigwarp.cc"
}
