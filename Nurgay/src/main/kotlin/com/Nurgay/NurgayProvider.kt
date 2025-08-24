package com.Nurgay

import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.Nurgay.Nurgay

@CloudstreamPlugin
class NurgayProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Nurgay())
        registerExtractorAPI(VID())
        registerExtractorAPI(Bigwarp())
        registerExtractorAPI(Bigwarpio())
        registerExtractorAPI(GXtapesnewExtractor())
        registerExtractorAPI(GXtape44Extractor())
        registerExtractorAPI(DoodExtractor())
        registerExtractorAPI(vide0Extractor())
        registerExtractorAPI(DoodExtractor())
        registerExtractorAPI(doodso())
        registerExtractorAPI(dsio())
        registerExtractorAPI(Voe())
    }
}
