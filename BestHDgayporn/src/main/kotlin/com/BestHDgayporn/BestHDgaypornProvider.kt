package com.BestHDgayporn

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.BestHDgayporn.BestHDgayporn
import com.lagradost.cloudstream3.extractors.*

@CloudstreamPlugin
class BestHDgaypornProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(BestHDgayporn())
    }
}
