package com.GXtapes

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.GXtapes.GXtapes

@CloudstreamPlugin
class GXtapesProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(GXtapes())
        registerExtractorAPI(Stream())
        registerExtractorAPI(VID())
        registerExtractorAPI(GXtapesExtractor())
        registerExtractorAPI(GXtapesnewExtractor())
        registerExtractorAPI(DoodExtractor())
    }
}
