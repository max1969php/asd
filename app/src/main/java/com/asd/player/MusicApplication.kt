package com.asd.player

import android.app.Application

class MusicApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        L10n.init(this)
        container = AppContainer(this)
    }
}
