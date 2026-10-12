package com.tele.child

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Only receives incoming SMS when Tele IS the default SMS app (the system sends SMS_DELIVER to the default app only).
 * It writes the message into the system SMS inbox so the child's texts are stored in one place.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val first = parts.firstOrNull() ?: return
        val body = parts.joinToString("") { it.messageBody ?: "" }
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, first.displayOriginatingAddress ?: first.originatingAddress ?: "")
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, first.timestampMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }

        val pending = goAsync()
        Thread {
            try {
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                SyncScheduler.syncNow(context)
            } catch (e: Exception) {
                Log.w("Tele", "Could not store incoming SMS", e)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
