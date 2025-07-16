package com.PornOneGay

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.StreamTape
import com.PornOneGay.PornOneGayProvider

@CloudstreamPlugin
class PornOneGayPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PornOneGayProvider())
    }
}