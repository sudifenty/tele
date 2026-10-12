package com.tele.child

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Runs ONLY after the child has accepted the consent screen and linked to a parent.
 * It shows a permanent notification (cannot be swiped away) with the required supervision notice.
 * It does NOT record audio, does NOT track location, and does NOT hide itself.
 */
class SupervisionService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    private val syncSoon = Runnable { SyncScheduler.syncNow(this) }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            // Debounce bursts of call-log / SMS changes into one upload.
            handler.removeCallbacks(syncSoon)
            handler.postDelayed(syncSoon, 3_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        runCatching { contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer) }
        runCatching { contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (!Prefs.isLinked(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        SyncScheduler.schedulePeriodic(this)
        SyncScheduler.syncNow(this)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(syncSoon)
        runCatching { contentResolver.unregisterContentObserver(observer) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, SupervisionService::class.java))
        }

        fun buildNotification(ctx: Context): Notification {
            val openApp = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notice = ctx.getString(R.string.supervision_notice)
            return NotificationCompat.Builder(ctx, NotificationChannels.SUPERVISION)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Tele")
                .setContentText(notice)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notice))
                .setContentIntent(openApp)
                .setOngoing(true)              // cannot be swiped away
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }
}
