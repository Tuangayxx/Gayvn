package com.Fxggxt

import android.content.Context
import com.Fxggxt.Fxggxt
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FxggxtPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Fxggxt())
        registerExtractorAPI(DoodExtractor())
    }
}
