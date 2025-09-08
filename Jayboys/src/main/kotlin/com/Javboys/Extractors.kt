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

class hypeboy : Voe() {
    override var mainUrl = "https://hypeboy.link"
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

class do7go : DoodLaExtractor() {
    override var mainUrl = "https://do7go.com"
}

class tapepops : StreamTape() {
    override var mainUrl = "https://tapepops.com"
    override var name = "tapepops"
}

class FileMoon : FilemoonV2() {
    override var mainUrl = "https://filemoon.to"
    override var name = "FileMoon"
}

fun reconstructDataUris(text: String, maxCollect: Int = 300_000): List<String> {
    val out = mutableListOf<String>()
    var idx = text.indexOf("data:video/")
    while (idx >= 0) {
        val baseStart = text.indexOf("base64,", idx)
        if (baseStart < 0) break
        val prefix = text.substring(idx, baseStart + 7) // includes "base64,"

        val sb = StringBuilder()
        var i = baseStart + 7
        var collected = 0

        while (i < text.length && collected < maxCollect) {
            val c = text[i]
            // các ký tự hợp lệ trong Base64
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '=') {
                sb.append(c)
                collected++
                i++
                continue
            }
            // bỏ qua ký tự nối chuỗi JS hoặc khoảng trắng
            if (c == '\'' || c == '\"' || c == '+' || c.isWhitespace()) {
                i++
                continue
            }
            // heuristics để dừng: gặp tag html hoặc ký tự lạ sau đã thu được 8 ký tự
            if ((c == '<' || c == '>') && collected > 8) break
            if (!c.isLetterOrDigit() && collected > 8) break
            i++
        }

        if (sb.isNotEmpty()) {
            out.add(prefix + sb.toString())
        }

        val nextFrom = if (i <= idx) idx + 1 else i
        idx = text.indexOf("data:video/", nextFrom)
    }
    return out
}

open class Base64Extractor : ExtractorApi() {
    override val name = "Base64"
    override val mainUrl = "base64"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // Nếu truyền vào chính là data-uri (mọi dạng data:) -> trả luôn, không gọi app.get
        if (url.startsWith("data:", ignoreCase = true)) {
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

        // Nếu URL http/https -> tải nội dung và tìm data-uri video base64 bên trong HTML/JS
        val responseText = try {
            app.get(url).text
        } catch (e: Exception) {
            // Không crash, chỉ log và trả null
            e.printStackTrace()
            return null
        }

        // Regex mạnh hơn: bắt mọi data:video/<mime>;base64,...
        val dataVideoRegex = Regex("""data:video\/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/=]+""", RegexOption.IGNORE_CASE)
        val firstMatch = dataVideoRegex.find(responseText)
        if (firstMatch != null) {
            val dataUri = firstMatch.value
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

        // Một số trang chèn src="data:video..." với quotes; thử tìm dạng src="..."
        val srcMatch = Regex("""src\s*=\s*["'](data:video\/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/=]+)["']""", RegexOption.IGNORE_CASE)
            .find(responseText)?.groupValues?.get(1)
        if (srcMatch != null) {
            return listOf(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = srcMatch,
                    type = INFER_TYPE
                ) {
                    this.referer = referer ?: mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }

        // Không tìm được data-uri video
        return null
    }
}

class JP : Base64Extractor() {
    override var mainUrl = "https://1069jp.com"
    override var name = "JP"
}

class hqq : Base64Extractor() {
    override var mainUrl = "https://hqq.tv"
    override var name = "hqq"
}

class jaygo : Base64Extractor() {
    override var mainUrl = "https://jaygo.link"
    override var name = "hqq"
}