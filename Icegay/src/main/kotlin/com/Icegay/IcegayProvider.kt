package com.Icegay

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class IcegayProvider : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Icegay())
    }
}
