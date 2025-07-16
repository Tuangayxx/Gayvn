package com.GPornOne

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class GPornOneProvider : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(GPornOne())
    }
}
