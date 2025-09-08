package com.iGay69

import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.iGay69.iGay69

@CloudstreamPlugin
class iGay69Provider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(iGay69())
        registerExtractorAPI(Bigwarp())
        registerExtractorAPI(Voe())
        registerExtractorAPI(VoeExtractor())
        registerExtractorAPI(dsio())
        registerExtractorAPI(DoodstreamCom())
        registerExtractorAPI(vide0())
        registerExtractorAPI(LuluStream())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(MxDrop())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(FilemoonV2())
    }
}
