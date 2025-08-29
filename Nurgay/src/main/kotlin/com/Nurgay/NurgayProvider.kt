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
        registerMainAPI(VoeExtractor())
        registerMainAPI(dsio())
        registerMainAPI(Bigwarp())
        registerMainAPI(vide0())
    }
}