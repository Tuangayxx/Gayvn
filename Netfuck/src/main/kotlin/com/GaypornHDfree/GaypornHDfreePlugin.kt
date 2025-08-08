package com.GaypornHDfree

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.*

@CloudstreamPlugin
class GaypornHDfreePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(GaypornHDfree())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(DoodLaExtractor())
        registerExtractorAPI(dsio())
        registerExtractorAPI(Voe())
        registerExtractorAPI(boynextdoors())
    }
}