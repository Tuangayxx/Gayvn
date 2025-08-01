package com.Jayboys

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class JayboysPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Jayboys())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(Stbturbo())
        registerExtractorAPI(1069jp())
        registerExtractorAPI(1069())
        registerExtractorAPI(boynextdoors())
    }
}