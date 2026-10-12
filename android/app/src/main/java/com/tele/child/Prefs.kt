package com.tele.child

import android.content.Context

/** Local state only. Nothing here is hidden from the child. */
object Prefs {
    private const val FILE = "tele_prefs"
    private const val KEY_CONSENT_AT = "consent_at"
    private const val KEY_FAMILY_ID = "family_id"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_LAST_CALL_ID = "last_call_id"
    private const val KEY_LAST_SMS_ID = "last_sms_id"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun consentAccepted(ctx: Context): Boolean = sp(ctx).getLong(KEY_CONSENT_AT, 0L) > 0L
    fun setConsentAccepted(ctx: Context) = sp(ctx).edit().putLong(KEY_CONSENT_AT, System.currentTimeMillis()).apply()

    fun familyId(ctx: Context): String? = sp(ctx).getString(KEY_FAMILY_ID, null)
    fun deviceId(ctx: Context): String? = sp(ctx).getString(KEY_DEVICE_ID, null)
    fun isLinked(ctx: Context): Boolean = familyId(ctx) != null && deviceId(ctx) != null

    fun setLinked(ctx: Context, familyId: String, deviceId: String) {
        sp(ctx).edit()
            .putString(KEY_FAMILY_ID, familyId)
            .putString(KEY_DEVICE_ID, deviceId)
            .putLong(KEY_LAST_CALL_ID, -1L)   // -1 = first sync sends only the newest records
            .putLong(KEY_LAST_SMS_ID, -1L)
            .apply()
    }

    /** -1 means "never synced"; otherwise the highest call-log _ID already uploaded. */
    fun lastCallId(ctx: Context): Long = sp(ctx).getLong(KEY_LAST_CALL_ID, -1L)
    fun setLastCallId(ctx: Context, id: Long) = sp(ctx).edit().putLong(KEY_LAST_CALL_ID, id).apply()

    fun lastSmsId(ctx: Context): Long = sp(ctx).getLong(KEY_LAST_SMS_ID, -1L)
    fun setLastSmsId(ctx: Context, id: Long) = sp(ctx).edit().putLong(KEY_LAST_SMS_ID, id).apply()
}
