package com.Jayboys

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.*

open class Stbturbo : ExtractorApi() {
    override var name = "Stbturbo"
    override var mainUrl = "https://gaystream.link/"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {
            this.document.let { document ->
                val finalLink = document.select("#video-player").attr("data-src")
                return listOf(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = httpsify(finalLink),
                        ExtractorLinkType.M3U8
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



class 1069 : Stbturbo() {
    override var name = "1069"
    override var mainUrl = "https://1069.website/"
    override val requiresReferer = false
}

class boynextdoors : Stbturbo() {
    override var name = "boynextdoors"
    override var mainUrl = "jilliandescribecompany.com/"
    override val requiresReferer = false
}

class 1069jp : Stbturbo() {
    override var name = "1069jp"
    override var mainUrl = "https://1069jp.com/f"
    override val requiresReferer = false
}
