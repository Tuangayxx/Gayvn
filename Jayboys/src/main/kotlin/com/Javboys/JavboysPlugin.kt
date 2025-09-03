package com.Jayboys

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.*

@CloudstreamPlugin
class JayboysPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Jayboys())
        registerExtractorAPI(dsio())
        registerExtractorAPI(VoeExtractor())
        registerExtractorAPI(Voe())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(yi069website())
        registerExtractorAPI(DoodLaExtractor())
        registerExtractorAPI(DoodstreamCom())
        registerExtractorAPI(vide0())
        registerExtractorAPI(tapepops())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(FilemoonV2())
        registerExtractorAPI(Base64Extractor())
        registerExtractorAPI(JP())
        registerExtractorAPI(jaygo())
        registerExtractorAPI(hypeboy())
        registerExtractorAPI(do7go())
    }
}
