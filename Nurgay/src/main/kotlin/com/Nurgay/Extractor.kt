package com.Nurgay

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.extractors.StreamTape
import org.json.JSONObject


open class VID : ExtractorApi() {
    override var name = "VID Xtapes"
    override var mainUrl = "https://vid.nurgay.to"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url).document.toString()
        val link = response.substringAfter("src: '").substringBefore("',")
        return listOf(
            newExtractorLink(
                this.name,
                this.name,
                link,
                type = INFER_TYPE
            )
            {
                this.referer = referer ?: ""
            }
        )
    }
}

open class Bigwarp : ExtractorApi() {
    override val name = "Bigwarp"
    override val mainUrl = "https://bigwarp.cc/"
    override val requiresReferer = false
    
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val soup = app.get(url, allowRedirects = true).text
        val fileUrlRegex = Regex("""file:"(https?://[^"]+)"""")
        val mp4Url = fileUrlRegex.find(soup)?.groupValues?.get(1) ?: return

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = mp4Url,
            )
        )
    }
}

class GXtapesnewExtractor(
    override val name: String = "88z.io",
    override val mainUrl: String = "https://88z.io",
    override val requiresReferer: Boolean = false
) : ExtractorApi() {
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        var found = false

        document.select("#video-code iframe").forEach { iframe ->
            val src = iframe.attr("src")
            val videoHash = src.substringAfter("/")
            val directUrl = "$mainUrl/$videoHash"
        callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    directUrl,
                    ExtractorLinkType.M3U8
                )
            )
        }
    }
}

class GXtape44Extractor(
    override val name: String = "44x.io",
    override val mainUrl: String = "https://vi.44x.io",
    override val requiresReferer: Boolean = false
) : ExtractorApi() {
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        var found = false

        document.select("#video-code iframe").forEach { iframe ->
            val src = iframe.attr("src")
            val videoHash = src.substringAfter("/")
            val directUrl = "$mainUrl/$videoHash"
        callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    directUrl,
                    ExtractorLinkType.M3U8
                )
            )
        }
    }
}

class doodso : DoodLaExtractor() {
    override var mainUrl = "https://dood.so"
}

class dsio : DoodLaExtractor() {
    override var mainUrl = "https://d-s.io"
}

open class DoodExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://d0000d.com"
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

open class vide0Extractor : ExtractorApi() {
    override var name = "vide0"
    override var mainUrl = "https://doooodstream.com"
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

open class StreamTapeExtractor: ExtractorApi() {
    override val mainUrl = "https://streamtape.com/e/"
    override val name = "StreamTape"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ){
        val newUrl = if (!url.startsWith(mainUrl)) {
            val id = url.split("/").getOrNull(4) ?: return
            mainUrl + id
        } else { url }

        val document = app.get(newUrl).document
        val targetLine = "document.getElementById('robotlink')"
        val script = document.selectFirst("script:containsData($targetLine)")
            ?.data()
            ?.substringAfter("$targetLine.innerHTML = '")
            ?: return
        val trueUrl = "https:" + script.substringBefore("'") +
                script.substringAfter("+ ('xcd").substringBefore("'")

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = trueUrl,
                type = INFER_TYPE
            )
        )
    }
}
