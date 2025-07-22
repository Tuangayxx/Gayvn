package com.GXtapes

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
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
import org.json.JSONObject


class Stream : Filesim() {
    override var mainUrl = "https://88z.io"
}


open class VID : ExtractorApi() {
    override var name = "VID Xtapes"
    override var mainUrl = "https://vid.xtapes.in"
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

class GXtapesExtractor(
    override val name: String = "GXtapes",
    override val mainUrl: String = "https://74k.io/e/",
    override val requiresReferer: Boolean = false
) : ExtractorApi() {
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url).document
        val script = response.select("script").find { it.data().contains("eval") }?.data() ?: return
        val unpackedScript = getAndUnpack(script)
        val links = "{" + unpackedScript.substringAfter("var links={").substringBefore("};") + "}"
        val obj = JSONObject(links)
        obj.keys().forEach {
            var finalUrl = obj.getString(it)
            if (!finalUrl.startsWith("http")) finalUrl = fixUrl(finalUrl)
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    finalUrl,
                    ExtractorLinkType.M3U8
                )
            )
        }
    }
}

class GXtapesnewExtractor(
    override val name: String = "88z.io",
    override val mainUrl: String = "https://88z.io/",
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
    override val mainUrl: String = "https://vi.44x.io/e",
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