package com.Gayxx

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.VidhideExtractor


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


class D0000d : DoodLaExtractor() {
    override var mainUrl = "https://d0000d.com"
}

class D000dCom : DoodLaExtractor() {
    override var mainUrl = "https://d000d.com"
}

class DoodstreamCom : DoodLaExtractor() {
    override var mainUrl = "https://doodstream.com"
}

class Dooood : DoodLaExtractor() {
    override var mainUrl = "https://dooood.com"
}

class DoodWfExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.wf"
}

class DoodCxExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.cx"
}

class DoodShExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.sh"
}
class DoodWatchExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.watch"
}

class DoodPmExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.pm"
}

class DoodToExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.to"
}

class DoodSoExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.so"
}

class DoodWsExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.ws"
}

class DoodYtExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.yt"
}

class vide0Extractor : DoodLaExtractor() {
    override var mainUrl = "https://vide0.net"
}

open class DoodLaExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://dood.la"
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/d/$id"
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val newUrl= url.replace(mainUrl, "https://d0000d.com")
        val response0 = app.get(newUrl).text // html of DoodStream page to look for /pass_md5/...
        val md5 ="https://d0000d.com"+(Regex("/pass_md5/[^']*").find(response0)?.value ?: return null)  // get https://dood.ws/pass_md5/...
        val trueUrl = app.get(md5, referer = newUrl).text + "zUEJeL3mUN?token=" + md5.substringAfterLast("/")   //direct link to extract  (zUEJeL3mUN is random)
        val quality = Regex("\\d{3,4}p").find(response0.substringAfter("<title>").substringBefore("</title>"))?.groupValues?.get(0)
        return listOf(
            newExtractorLink(
                this.name,
                this.name,
                trueUrl,
                getQualityFromName(quality),
                false
            )
        ) // links are valid in 8h

    }
}





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
import kotlinx.coroutines.delay 

class MixDropBz : MixDrop(){
    override var mainUrl = "https://mixdrop.bz"
}

class MixDropCh : MixDrop(){
    override var mainUrl = "https://mixdrop.ch"
}

class MixDropTo : MixDrop() { 
    override var mainUrl = "https://mixdrop.to"
}

open class MixDrop : ExtractorApi() {
    override var name = "MixDrop"
    override var mainUrl = "https://mixdrop.co"
    private val srcRegex = Regex("""(?:wurl|MDCore\.wurl)\s*=\s*['"]([^'"]+)['"]""") // REGEX TỐT HƠN
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/e/$id"
    }

    // THÊM PHƯƠNG THỨC XỬ LÝ LINK
    override suspend fun getExtractorLinks(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(url, referer = referer)
        val doc = response.document
        
        // Tìm và giải mã script
        val script = doc.selectFirst("script:containsData(wurl)")?.data() ?: return false
        val unpacker = JsUnpacker(script)
        val unpacked = unpacker.unpackIfNeeded() ?: script
        
        // Trích xuất URL video
        val videoPath = srcRegex.find(unpacked)?.groupValues?.get(1) ?: return false
        val videoUrl = "https:${videoPath.replace("\\/", "/")}"

        // Xử lý HLS/Direct
        when {
            videoUrl.contains(".m3u8") -> {
                M3u8Helper.generateM3u8(
                    name,
                    videoUrl,
                    mainUrl,
                ).forEach(callback)
            }
            else -> {
                callback(
                    newExtractorLink(
                        name,
                        videoUrl,
                        mainUrl,
                        Qualities.Unknown.value,
                        type = ExtractorLinkType.VIDEO
                    )
                )
            }
        }
        return true
    }
}




class StreamTapeNet : StreamTape() {
    override var mainUrl = "https://streamtape.net"
}

class StreamTapeXyz : StreamTape() {
    override var mainUrl = "https://streamtape.xyz"
}

class ShaveTape : StreamTape(){
    override var mainUrl = "https://shavetape.cash"
}

open class StreamTape : ExtractorApi() {
    override var name = "StreamTape"
    override var mainUrl = "https://streamtape.com"
    override val requiresReferer = false

    private val linkRegex =
        Regex("""'robotlink'\)\.innerHTML = '(.+?)'\+ \('(.+?)'\)""")

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {
            linkRegex.find(this.text)?.let {
                val extractedUrl =
                    "https:${it.groups[1]!!.value + it.groups[2]!!.value.substring(3)}"
                return listOf(
                    newExtractorLink(
                        name,
                        name,
                        extractedUrl,
                        url,
                        Qualities.Unknown.value,
                    )
                )
            }
        }
        return null
    }
}