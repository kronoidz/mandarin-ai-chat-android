package com.mandarin.aichat

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        PinyinDict.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
