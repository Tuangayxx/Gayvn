package com.DvdGayOnline

import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.DvdGayOnline.DvdGayOnline

@CloudstreamPlugin
class DvdGayOnlineProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DvdGayOnline())
        registerExtractorAPI(Bigwarp())
        registerExtractorAPI(Voe())
        registerExtractorAPI(VoeExtractor())
        registerExtractorAPI(dsio())
        registerExtractorAPI(DoodstreamCom())
        registerExtractorAPI(vide0())
        registerExtractorAPI(ListMirror())
    }
}
