package com.tele.child

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Real-time location sharing with the parent.
 *  - Runs as a visible foreground service with its own notice ("Tele is sharing this phone's location with Dad").
 *  - Starts only if the child allowed location, and only after the child has linked to a parent.
 *  - The parent can pause sharing from the dashboard. The child's notice then says it is paused.
 *  - Updates are sent about once a minute. Android may delay them when the phone is in deep sleep.
 */
class LocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: FusedLocationProviderClient
    private var flagListener: ListenerRegistration? = null
    private var updatesActive = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            scope.launch {
                runCatching { Repo.uploadLocation(this@LocationService, loc) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Checked before startForeground: the location foreground type needs the permission at runtime.
        if (!canStart(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(this, sharing = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        watchSharingFlag()
        return START_STICKY
    }

    /** The parent's "locationSharing" switch on the device record controls whether we send updates. */
    private fun watchSharingFlag() {
        if (flagListener != null) return
        val ref = Repo.deviceRef(this) ?: return
        flagListener = ref.addSnapshotListener { snap, _ ->
            val share = snap?.getBoolean("locationSharing") ?: true
            if (share) startUpdates() else stopUpdates()
        }
    }

    @SuppressLint("MissingPermission")  // checked in canStart()
    private fun startUpdates() {
        if (updatesActive) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        updatesActive = true
        updateNotification(sharing = true)
    }

    private fun stopUpdates() {
        if (updatesActive) client.removeLocationUpdates(callback)
        updatesActive = false
        updateNotification(sharing = false)
    }

    private fun updateNotification(sharing: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(this, sharing))
    }

    override fun onDestroy() {
        flagListener?.remove()
        if (updatesActive) client.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1002
        private const val UPDATE_INTERVAL_MS = 60_000L
        private const val MIN_INTERVAL_MS = 30_000L

        fun canStart(ctx: Context): Boolean = Prefs.isLinked(ctx) && Permissions.hasLocationAlways(ctx)

        /** Starts only when allowed. Does nothing otherwise, so the child is never surprised by a service it didn't allow. */
        fun start(ctx: Context) {
            if (canStart(ctx)) ContextCompat.startForegroundService(ctx, Intent(ctx, LocationService::class.java))
        }

        fun buildNotification(ctx: Context, sharing: Boolean): Notification {
            val openApp = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val text = if (sharing) "Tele is sharing this phone's location with Dad now."
                       else "Location sharing is paused by Dad."
            return NotificationCompat.Builder(ctx, NotificationChannels.LOCATION)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Tele: location")
                .setContentText(text)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }
}
