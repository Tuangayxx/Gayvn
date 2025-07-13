package com.Gayxx

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Document

// Tạo lớp cơ sở cho các extractor video
abstract class GayxxExtractor : ExtractorApi() {
    protected abstract val domain: String
    override val mainUrl: String get() = "https://$domain"
    
    protected fun newHlsLink(
        url: String,
        quality: Int = Qualities.Unknown.value,
        name: String = this.name
    ) = newExtractorLink(
        name = name,
        source = this.name,
        url = url,
        quality = quality,
        type = ExtractorLinkType.HLS
    )
}

// Stream Extractor - Không cần thay đổi nhiều
class Stream : GayxxExtractor() {
    override val name = "Stream"
    override val domain = "vide0.net"
    override val mainUrl = "https://$domain/e"
}

// Voe Extractor - Tối ưu regex và xử lý lỗi
class VoeExtractor : GayxxExtractor() {
    override val name = "Voe"
    override val domain = "voe.sx"
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
            ?.replace("0,", "0") // Fix JSON syntax issue
            ?: return emptyList()

        return tryParseJson<VideoSource>(jsonMatch)?.let { source ->
            source.url?.let { url ->
                listOf(newHlsLink(url, source.height ?: Qualities.Unknown.value))
            } ?: emptyList()
        } ?: emptyList()
    }
}

// Vide0 Extractor - Tối ưu selector và xử lý URL
class Vide0Extractor : GayxxExtractor() {
    override val name = "vide0"
    override val domain = "vide0.net"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document
        
        doc.select("#video-code iframe[src]").mapNotNull { iframe ->
            iframe.attr("src").takeIf { it.isNotBlank() }?.substringAfterLast("/")
        }.forEach { videoId ->
            callback(newHlsLink("$mainUrl/e/$videoId"))
        }
    }
}
