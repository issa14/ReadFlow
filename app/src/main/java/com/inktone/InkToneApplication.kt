package com.inktone

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class InkToneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PerfLogger.init(this)
        PerfLogger.markAppStart()
    }
}
