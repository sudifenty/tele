package com.tele.child

import android.app.Application

class TeleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
    }
}
