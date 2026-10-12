package com.tele.child

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val SUPERVISION = "supervision"
    const val LOCATION = "location"

    fun create(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            SUPERVISION,
            ctx.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW   // persistent and visible, but silent
        ).apply {
            description = ctx.getString(R.string.supervision_notice)
            setShowBadge(false)
        })
        manager.createNotificationChannel(NotificationChannel(
            LOCATION,
            "Location sharing",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while your location is shared with your parent"
            setShowBadge(false)
        })
    }
}
