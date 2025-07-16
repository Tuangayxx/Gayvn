package com.PornOneGay

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.StreamTape
import com.PornOneGay.PornOneGay

@CloudstreamPlugin
class PornOnePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PornOneGay())
    }
}