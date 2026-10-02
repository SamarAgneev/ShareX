package com.sharex.app

import android.app.Application

class ShareXApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

val android.content.Context.graph: AppGraph
    get() = (applicationContext as ShareXApp).graph
