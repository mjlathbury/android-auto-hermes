package com.hermes.drive

import android.app.Application

class HermesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ChatNotificationManager.ensureChannel(this)
    }
}
