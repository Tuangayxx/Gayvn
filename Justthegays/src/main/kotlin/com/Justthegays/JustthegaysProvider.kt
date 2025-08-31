package com.Justthegays

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.Justthegays.Justthegays
import com.lagradost.cloudstream3.extractors.*

@CloudstreamPlugin
class JustthegaysProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Justthegays())
    }
}
