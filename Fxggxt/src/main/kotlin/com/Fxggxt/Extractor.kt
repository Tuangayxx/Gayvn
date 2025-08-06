package com.Fxggxt

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.USER_AGENT
import org.json.JSONObject
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import org.jsoup.Jsoup
import android.annotation.SuppressLint

abstract class BaseVideoExtractor : ExtractorApi() {
    protected abstract val domain: String
    override val mainUrl: String get() = "https://$domain"
}

class dsio : BaseVideoExtractor() {
    override val name = "dsio"
    override val domain = "d-s.io"
    override val mainUrl = "https://$domain"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
            val response0 = app.get(url).text

            val passMd5Path = Regex("/pass_md5/[^'\"]+").find(response0)?.value ?: return null
            val token = passMd5Path.substringAfterLast("/")
        
            val md5Url = mainUrl + passMd5Path
            val res = app.get(md5Url, referer = referer) // Sử dụng URL gốc làm referer
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


class dsExtractor : BaseVideoExtractor() {
    override var name = "dsExtractor"
    override val domain = "d-s.io"
    override val mainUrl = "https://$domain"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // Sửa lỗi: Tạo chuỗi ngẫu nhiên đúng cách
        val randomStr = generateRandomString(10)

        val proxyUrl = url.replace("doodstream.com", "d-s.io")
        val response = app.get(proxyUrl, referer = proxyUrl).text

        // Tìm token từ hàm makePlay()
        val tokenRegex = Regex("""makePlay\(\)\s*{[^}]*token=([\w]+)""")
        val token = tokenRegex.find(response)?.groupValues?.get(1) ?: return null

        // Tìm URL pass_md5
        val passMd5Regex = Regex("""\$\get\('(/pass_md5/[^']+)'\)""")
        val passMd5Path = passMd5Regex.find(response)?.groupValues?.get(1) ?: return null

        // Lấy dữ liệu video
        val md5Url = proxyUrl + passMd5Path
        val videoData = app.get(md5Url, referer = proxyUrl).text

        // Tạo URL cuối cùng
        val expiry = System.currentTimeMillis()
        val trueUrl = "$videoData$randomStr?token=$token&expiry=$expiry"

        return listOf(
            newExtractorLink(
                source = name,
                name = "Dsio",
                url = trueUrl,
                type = INFER_TYPE
            ) {
                this.referer = proxyUrl
            }
        )
    }

    // Hàm tạo chuỗi ngẫu nhiên đúng
    private fun generateRandomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { charPool.random() }
            .joinToString("")
    }
}


class doodst : BaseVideoExtractor() {
    override val name = "doodst"
    override val domain = "doodstream.com"
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


open class dood : ExtractorApi() {
    override var name = "dood"
    override var mainUrl = "https://vvide0.com"
    override val requiresReferer = true // Bật yêu cầu referer

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val changeurl=url.replace("doodstream.com","vvide0.com")
        val response0 = app.get(changeurl, referer = referer).text
        
        // Tìm đường dẫn pass_md5
        val passMd5Path = Regex("/pass_md5/[^'\"]+").find(response0)?.value ?: return null
        val token = passMd5Path.substringAfterLast("/")
        
        // Lấy dữ liệu video
        val md5Url = mainUrl + passMd5Path
        val res = app.get(md5Url, referer = url) // Sử dụng URL gốc làm referer
        val videoData = res.text
        
        // Tạo chuỗi ngẫu nhiên chính xác
        val randomStr = (1..10).map { 
            (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() 
        }.joinToString("")
        
        // Tạo URL hoàn chỉnh
        val trueUrl = "$videoData$randomStr?token=$token&expiry=${System.currentTimeMillis()}"
        
        // Lấy chất lượng video (cải tiến regex)
        val quality = Regex("(\\d{3,4})[pP]")
            .find(response0.substringAfter("<title>").substringBefore("</title>"))
            ?.groupValues?.get(1)

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
    override var mainUrl = "https://vide0.net"
    override val requiresReferer = true // Bật yêu cầu referer

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        
        val changeurl=url.replace("doodstream.com","vide0.net")
        val response0 = app.get(changeurl, referer = referer).text


        // Tìm đường dẫn pass_md5
        val passMd5Path = Regex("/pass_md5/[^'\"]+").find(response0)?.value ?: return null
        val token = passMd5Path.substringAfterLast("/")
        
        // Lấy dữ liệu video
        val md5Url = mainUrl + passMd5Path
        val res = app.get(md5Url, referer = url) // Sử dụng URL gốc làm referer
        val videoData = res.text
        
        // Tạo chuỗi ngẫu nhiên chính xác
        val randomStr = (1..10).map { 
            (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() 
        }.joinToString("")
        
        // Tạo URL hoàn chỉnh
        val trueUrl = "$videoData$randomStr?token=$token&expiry=${System.currentTimeMillis()}"
        
        // Lấy chất lượng video (cải tiến regex)
        val quality = Regex("(\\d{3,4})[pP]")
            .find(response0.substringAfter("<title>").substringBefore("</title>"))
            ?.groupValues?.get(1)

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