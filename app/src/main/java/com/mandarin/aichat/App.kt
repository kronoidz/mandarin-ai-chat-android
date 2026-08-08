package com.mandarin.aichat

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        TtsAudioCache.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
