package com.Netfuck

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class NetfuckPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Netfuck())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(Stbturbo())
        registerExtractorAPI(yilingjp())
        registerExtractorAPI(yiling())
        registerExtractorAPI(boynextdoors())
    }
}