package com.topHDgayporn

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.topHDgayporn.topHDgayporn
import com.lagradost.cloudstream3.extractors.*

@CloudstreamPlugin
class topHDgaypornProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(topHDgayporn())
    }
}
