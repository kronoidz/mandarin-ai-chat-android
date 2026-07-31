package com.mandarin.aichat

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        PinyinDict.init(this)
    }
}
