package com.HDgay

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.extractors.*
import android.content.Context
import com.HDgay.HDgay

@CloudstreamPlugin
class HDgayProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDgay())
        registerExtractorAPI(VoeExtractor())
        registerExtractorAPI(vvide0Extractor())
        registerExtractorAPI(HdgayPlayer())
        registerExtractorAPI(dsio())
        registerExtractorAPI(Doply())
        registerExtractorAPI(Voe())
    }
}
