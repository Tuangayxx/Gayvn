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

open class WaawExtractor : ExtractorApi() {
    override val name = "Waaw"
    override val mainUrl = "https://waaw.to"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val res = app.get(url)
        if (res.code == 404) return emptyList()

        val text = res.text
        val doc = try { res.document } catch (e: Exception) { null }

        val found = linkedSetOf<String>()

        // 1) iframe[src]
        try {
            doc?.select("iframe[src]")?.forEach { el ->
                el.attr("abs:src")?.takeIf { it.isNotBlank() }?.let { found.add(it) }
            }
        } catch (_: Exception) {}

        // 2) common data attributes (data-src, data-video, data-url)
        try {
            doc?.select("[data-src],[data-video],[data-url]")?.forEach { el ->
                listOf("data-src", "data-video", "data-url").forEach { attr ->
                    el.attr("abs:$attr")?.takeIf { it.isNotBlank() }?.let { found.add(it) }
                }
                // sometimes src attribute on the element
                el.attr("abs:src")?.takeIf { it.isNotBlank() }?.let { found.add(it) }
            }
        } catch (_: Exception) {}

        // 3) scan <script> blocks: try JsUnpacker, search for .m3u8/.mp4 and base64 strings
        try {
            doc?.select("script")?.forEach { s ->
                val data = s.data()
                if (data.isNullOrBlank()) return@forEach

                // Try unpacker first (handles p.a.c.k.e.r and similar)
                JsUnpacker(data).unpack()?.let { unpacked ->
                    Regex("""https?://[^'"\s\\]+\.m3u8[^'"\s\\]*""").findAll(unpacked).forEach { found.add(it.value) }
                    Regex("""https?://[^'"\s\\]+\.mp4[^'"\s\\]*""").findAll(unpacked).forEach { found.add(it.value) }

                    // try to find base64 blobs in unpacked and decode
                    Regex("""["']([A-Za-z0-9+/=]{40,})["']""").findAll(unpacked).forEach { m ->
                        try {
                            val decoded = String(Base64.getDecoder().decode(m.groupValues[1]))
                            Regex("""https?://[^'"\s\\]+""").findAll(decoded).forEach { found.add(it.value) }
                        } catch (_: Exception) {}
                    }
                }

                // Also directly search the raw script
                Regex("""https?://[^'"\s\\]+\.m3u8[^'"\s\\]*""").findAll(data).forEach { found.add(it.value) }
                Regex("""https?://[^'"\s\\]+\.mp4[^'"\s\\]*""").findAll(data).forEach { found.add(it.value) }
            }
        } catch (_: Exception) {}

        // 4) fallback: search whole page text for m3u8 / mp4
        try {
            Regex("""https?://[^'"\s\\]+\.m3u8[^'"\s\\]*""").findAll(text).forEach { found.add(it.value) }
            Regex("""https?://[^'"\s\\]+\.mp4[^'"\s\\]*""").findAll(text).forEach { found.add(it.value) }
        } catch (_: Exception) {}

        if (found.isEmpty()) return emptyList()

        // Build ExtractorLink list (de-dupe)
        return found.map { link ->
            newExtractorLink(
                name = name,
                source = name,
                url = link,
                type = INFER_TYPE
            ) {
                // set referer to original page in case remote host needs it
                this.referer = url
                // try to extract quality from filename (e.g. 720p, 1080)
                val qualityMatch = Regex("""(\d{3,4}p)""").find(link)?.groupValues?.get(1) ?: ""
                this.quality = Qualities.Unknown.value
            }
        }
    }
}
