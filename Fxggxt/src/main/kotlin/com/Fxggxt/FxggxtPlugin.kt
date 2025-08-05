package com.Fxggxt

import android.content.Context
import com.Fxggxt.Fxggxt
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.extractors.DoodLaExtractor

@CloudstreamPlugin
class FxggxtPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Fxggxt())
        registerExtractorAPI(vide0Extractor())
        registerExtractorAPI(dood())
        registerExtractorAPI(dsio())
        registerExtractorAPI(dsExtractor())
        registerExtractorAPI(doodst())
        registerExtractorAPI(DoodLaExtractor())
    }
}
