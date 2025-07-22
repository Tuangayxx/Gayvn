package com.Nurgay

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.Nurgay.Nurgay

@CloudstreamPlugin
class NurgayProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Nurgay())
        registerExtractorAPI(Stream())
        registerExtractorAPI(VID())
        registerExtractorAPI(Bigwarp())
        registerExtractorAPI(GXtapesnewExtractor())
        registerExtractorAPI(GXtape44Extractor())
        registerExtractorAPI(DoodExtractor())
    }
}
