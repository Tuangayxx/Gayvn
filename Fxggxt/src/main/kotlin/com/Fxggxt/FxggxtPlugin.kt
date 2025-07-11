package com.Fxggxt

import android.content.Context
import com.Fxggxt.Fxggxt
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class FxggxtPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Fxggxt())
        registerExtractorAPI(StreamTape())

    }
}
