package com.tele.child

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import android.location.Location
import java.util.Date
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Firestore layout (see firebase/firestore.rules):
 *   families/{familyId}/devices/{deviceId}            device status, sosContactPhone (set by parent)
 *   families/{familyId}/devices/{deviceId}/calls/c{id} call metadata
 *   families/{familyId}/devices/{deviceId}/sms/s{id}   SMS metadata, body only when default SMS app
 *   families/{familyId}/devices/{deviceId}/sos/{auto} SOS events
 *   pairings/{6-digit code}                            short-lived link codes created by the parent
 */
object Repo {
    private const val PAGE = 200
    private const val HISTORY_DAYS = 7L
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    suspend fun ensureSignedIn(): String {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user!!.uid
    }

    fun deviceRef(ctx: Context): DocumentReference? {
        val fid = Prefs.familyId(ctx) ?: return null
        val did = Prefs.deviceId(ctx) ?: return null
        return db.collection("families").document(fid).collection("devices").document(did)
    }

    @Suppress("DEPRECATION")
    private fun appVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "unknown"

    /** Links this phone to a family using a parent-generated code. Creates a new device record each time. */
    suspend fun linkDevice(ctx: Context, code: String) {
        val uid = ensureSignedIn()
        val pairing = db.collection("pairings").document(code).get().await()
        if (!pairing.exists()) throw IllegalStateException("That code was not found.")
        val familyId = pairing.getString("familyId") ?: throw IllegalStateException("That code is invalid.")
        val expiresAt = pairing.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
        if (expiresAt < System.currentTimeMillis()) {
            throw IllegalStateException("That code expired. Ask Dad to create a new one.")
        }

        val deviceId = UUID.randomUUID().toString()
        db.collection("families").document(familyId).collection("devices").document(deviceId).set(
            mapOf(
                "ownerUid" to uid,
                "pairingCode" to code,
                "label" to Build.MODEL,
                "linkedAt" to FieldValue.serverTimestamp(),
                "lastSeen" to FieldValue.serverTimestamp(),
                "defaultSms" to SmsRole.isDefault(ctx),
                "appVersion" to appVersion(ctx),
                "permissions" to Permissions.status(ctx)
            )
        ).await()

        Prefs.setLinked(ctx, familyId, deviceId)
        // Consume the code so it cannot be reused (best effort).
        runCatching { db.collection("pairings").document(code).delete().await() }
    }

    /** Status shown to the parent: last seen, default SMS state, and which permissions are allowed. */
    suspend fun heartbeat(ctx: Context) {
        val ref = deviceRef(ctx) ?: return
        ref.set(
            mapOf(
                "lastSeen" to FieldValue.serverTimestamp(),
                "defaultSms" to SmsRole.isDefault(ctx),
                "appVersion" to appVersion(ctx),
                "permissions" to Permissions.status(ctx)
            ),
            SetOptions.merge()
        ).await()
    }

    /** Uploads only records that are new since the last successful upload. */
    suspend fun syncAll(ctx: Context) {
        val ref = deviceRef(ctx) ?: return
        ensureSignedIn()
        heartbeat(ctx)

        val defaultSms = SmsRole.isDefault(ctx)

        var callAfter: Long? = Prefs.lastCallId(ctx).takeIf { it >= 0 }
        while (true) {
            val batch = CallLogReader.read(ctx, callAfter, PAGE)
            if (batch.isEmpty()) break
            commit(ref.collection("calls"), batch.map { c ->
                "c${c.id}" to mapOf<String, Any?>(
                    "id" to c.id,
                    "number" to c.number,
                    "name" to c.name,
                    "durationSec" to c.durationSec,
                    "ts" to c.ts,
                    "type" to c.type
                )
            })
            callAfter = batch.maxOf { it.id }
            Prefs.setLastCallId(ctx, callAfter)
            if (batch.size < PAGE) break
        }

        var smsAfter: Long? = Prefs.lastSmsId(ctx).takeIf { it >= 0 }
        while (true) {
            // includeBody is tied to the default-SMS state. When Tele is not default, bodies are never read.
            val batch = SmsReader.read(ctx, smsAfter, PAGE, includeBody = defaultSms)
            if (batch.isEmpty()) break
            commit(ref.collection("sms"), batch.map { m ->
                "s${m.id}" to mapOf<String, Any?>(
                    "id" to m.id,
                    "address" to m.address,
                    "name" to m.name,
                    "ts" to m.ts,
                    "folder" to m.folder,
                    "body" to m.body,               // null unless default SMS app
                    "bodyVisible" to m.bodyVisible
                )
            })
            smsAfter = batch.maxOf { it.id }
            Prefs.setLastSmsId(ctx, smsAfter)
            if (batch.size < PAGE) break
        }
    }

    private suspend fun commit(col: CollectionReference, docs: List<Pair<String, Map<String, Any?>>>) {
        docs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (id, data) -> batch.set(col.document(id), data, SetOptions.merge()) }
            batch.commit().await()
        }
    }

    /** SOS event. Location is included only if the child allowed it when pressing SOS. */
    suspend fun sendSos(ctx: Context, lat: Double?, lng: Double?, accuracyM: Float?) {
        val ref = deviceRef(ctx) ?: throw IllegalStateException("Not linked")
        ensureSignedIn()
        ref.collection("sos").add(
            mapOf(
                "ts" to System.currentTimeMillis(),
                "lat" to lat,
                "lng" to lng,
                "accuracyM" to accuracyM?.toDouble(),
                "withLocation" to (lat != null && lng != null)
            )
        ).await()
    }

    /**
     * Real-time location. "location/current" holds the latest point for the live map.
     * "locationHistory" keeps recent points for the trail. Each point has expiresAt (set a TTL policy in Firestore).
     */
    suspend fun uploadLocation(ctx: Context, loc: Location) {
        val ref = deviceRef(ctx) ?: return
        ensureSignedIn()
        val point: Map<String, Any> = mapOf(
            "lat" to loc.latitude,
            "lng" to loc.longitude,
            "accuracyM" to loc.accuracy.toDouble(),
            "ts" to loc.time
        )
        val batch = db.batch()
        batch.set(ref.collection("location").document("current"), point)
        batch.set(
            ref.collection("locationHistory").document(),
            point + mapOf("expiresAt" to Timestamp(Date(loc.time + HISTORY_DAYS * 86_400_000L)))
        )
        batch.commit().await()
    }

    /** The emergency contact phone the parent set in the dashboard. */
    suspend fun sosContactPhone(ctx: Context): String? {
        val snap = deviceRef(ctx)?.get()?.await() ?: return null
        return snap.getString("sosContactPhone")?.takeIf { it.isNotBlank() }
    }
}
