package com.GPorntrex

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class GPorntrexProvider : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(GPorntrex())
    }
}
