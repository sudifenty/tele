package com.tele.child

import android.content.Context
import android.provider.Telephony

object SmsReader {
    private val PROJECTION_METADATA = arrayOf(
        Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.TYPE
    )
    private val PROJECTION_WITH_BODY = arrayOf(
        Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.BODY
    )

    /**
     * SMS log. The BODY column is only queried when includeBody is true, and the caller passes
     * includeBody = SmsRole.isDefault(ctx). If Tele is not the default SMS app, text content is never read.
     */
    fun read(ctx: Context, afterId: Long?, limit: Int, includeBody: Boolean): List<SmsEntry> {
        if (!Permissions.granted(ctx, android.Manifest.permission.READ_SMS)) return emptyList()

        val projection = if (includeBody) PROJECTION_WITH_BODY else PROJECTION_METADATA
        val selection = StringBuilder("${Telephony.Sms.TYPE} IN (${Telephony.Sms.MESSAGE_TYPE_INBOX},${Telephony.Sms.MESSAGE_TYPE_SENT})")
        val args = mutableListOf<String>()
        if (afterId != null) {
            selection.append(" AND ${Telephony.Sms._ID} > ?")
            args += afterId.toString()
        }
        val order = if (afterId == null) "${Telephony.Sms._ID} DESC LIMIT $limit"
                    else "${Telephony.Sms._ID} ASC LIMIT $limit"

        val out = mutableListOf<SmsEntry>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, projection, selection.toString(), args.toTypedArray(), order
        )?.use { c ->
            while (c.moveToNext()) {
                val address = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Unknown"
                val type = c.getInt(3)
                val body: String? = if (includeBody) c.getString(4) else null
                out += SmsEntry(
                    id = c.getLong(0),
                    address = address,
                    name = CallLogReader.contactNameFor(ctx, address),
                    ts = c.getLong(2),
                    folder = if (type == Telephony.Sms.MESSAGE_TYPE_SENT) "sent" else "inbox",
                    body = body,
                    bodyVisible = includeBody
                )
            }
        }
        return if (afterId == null) out.reversed() else out
    }
}
