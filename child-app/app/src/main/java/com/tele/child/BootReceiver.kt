package com.tele.child

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the VISIBLE supervision services after reboot, so supervision is never silently off. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.consentAccepted(context) || !Prefs.isLinked(context)) return
        SupervisionService.start(context)
        // Android may refuse a location service started at boot. If so, it starts when the app is next opened.
        runCatching { LocationService.start(context) }
    }
}
