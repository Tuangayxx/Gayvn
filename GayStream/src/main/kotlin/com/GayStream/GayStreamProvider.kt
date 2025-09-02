package com.GayStream

import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.GayStream.GayStream

@CloudstreamPlugin
class GayStreamProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(GayStream())
        registerExtractorAPI(Bigwarp())
        registerExtractorAPI(Voe())
        registerExtractorAPI(VoeExtractor())
        registerExtractorAPI(dsio())
        registerExtractorAPI(DoodstreamCom())
        registerExtractorAPI(vide0())
        registerExtractorAPI(ListMirror())
        registerExtractorAPI(FilemoonV2())
    }
}
