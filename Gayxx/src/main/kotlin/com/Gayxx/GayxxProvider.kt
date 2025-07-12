package com.Gayxx

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.Gayxx.Gayxx

@CloudstreamPlugin
class GayxxProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Gayxx())
        registerExtractorAPI(Stream())
        registerExtractorAPI(VoeExtractor())
        registerExtractorAPI(vide0Extractor())
    }
}
