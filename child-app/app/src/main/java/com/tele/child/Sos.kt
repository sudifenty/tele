package com.tele.child

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.telephony.SmsManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

object Sos {
    /** Called only after the child pressed SOS and confirmed. Never runs in the background. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(ctx: Context): Location? {
        if (!Permissions.granted(ctx, Permissions.LOCATION)) return null
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val fresh = runCatching {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
        }.getOrNull()
        return fresh ?: runCatching { client.lastLocation.await() }.getOrNull()
    }

    /** Text the parent-chosen emergency number (set in the parent dashboard). Requires SEND_SMS. */
    @Suppress("DEPRECATION")
    fun textEmergencyContact(ctx: Context, phone: String, location: Location?) {
        val where = if (location != null)
            "My location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        else
            "Location was not shared."
        val text = "Tele SOS from ${Build.MODEL}: I need help. $where"
        val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
                  else SmsManager.getDefault()
        sms.sendTextMessage(phone, null, text, null, null)
    }
}
