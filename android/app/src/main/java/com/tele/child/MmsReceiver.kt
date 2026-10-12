package com.tele.child

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Required so Tele can be the default SMS app. MMS (picture messages) is NOT logged in this version.
 * See README "Known limitations".
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Intentionally empty.
    }
}
