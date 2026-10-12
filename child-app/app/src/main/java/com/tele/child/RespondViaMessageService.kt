package com.tele.child

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Required for the default-SMS role ("quick reply to a call"). Not implemented in this version.
 */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
