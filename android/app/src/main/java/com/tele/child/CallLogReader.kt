package com.tele.child

import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract

object CallLogReader {
    private val PROJECTION = arrayOf(
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.DURATION,
        CallLog.Calls.DATE,
        CallLog.Calls.TYPE
    )

    /**
     * Metadata only: number, name if saved, duration, time, direction/type.
     * afterId == null -> newest [limit] records. Otherwise records with _ID > afterId, oldest first.
     */
    fun read(ctx: Context, afterId: Long?, limit: Int): List<CallEntry> {
        if (!Permissions.granted(ctx, android.Manifest.permission.READ_CALL_LOG)) return emptyList()

        val selection = if (afterId == null) null else "${CallLog.Calls._ID} > ?"
        val args = if (afterId == null) null else arrayOf(afterId.toString())
        val order = if (afterId == null) "${CallLog.Calls._ID} DESC LIMIT $limit"
                    else "${CallLog.Calls._ID} ASC LIMIT $limit"

        val out = mutableListOf<CallEntry>()
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, PROJECTION, selection, args, order)?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Private"
                val cachedName = c.getString(2)?.takeIf { it.isNotBlank() }
                out += CallEntry(
                    id = c.getLong(0),
                    number = number,
                    name = cachedName ?: contactNameFor(ctx, number),
                    durationSec = c.getLong(3),
                    ts = c.getLong(4),
                    type = typeName(c.getInt(5))
                )
            }
        }
        return if (afterId == null) out.reversed() else out
    }

    private fun typeName(type: Int): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "incoming"
        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
        CallLog.Calls.MISSED_TYPE -> "missed"
        CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
        CallLog.Calls.REJECTED_TYPE -> "rejected"
        6 -> "blocked" // CallLog.Calls.BLOCKED_TYPE (API 24+)
        else -> "other"
    }

    /** Name from contacts. Only runs if the child allowed READ_CONTACTS. */
    fun contactNameFor(ctx: Context, number: String): String? {
        if (number == "Private" || !Permissions.granted(ctx, android.Manifest.permission.READ_CONTACTS)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }
}
