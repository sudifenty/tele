package com.tele.child

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.tele.child.databinding.ActivityComposeBinding

/**
 * Minimal send screen. Required for the default-SMS role (Android expects the default app to
 * handle SMS sending). Sending uses SEND_SMS, which the child is asked for openly.
 */
class ComposeActivity : AppCompatActivity() {
    private lateinit var b: ActivityComposeBinding

    private val smsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) send() else showPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityComposeBinding.inflate(layoutInflater)
        setContentView(b.root)

        val to = intent?.data?.schemeSpecificPart?.substringBefore('?').orEmpty()
        b.toInput.setText(to)
        b.bodyInput.setText(intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty())

        b.btnSend.setOnClickListener {
            if (Permissions.granted(this, Manifest.permission.SEND_SMS)) send()
            else smsPermission.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun showPermissionDenied() {
        b.composeStatus.text = "SMS permission was not allowed, so Tele cannot send texts."
    }

    @Suppress("DEPRECATION")
    private fun send() {
        val to = b.toInput.text.toString().trim()
        val body = b.bodyInput.text.toString().trim()
        if (to.isEmpty() || body.isEmpty()) {
            b.composeStatus.text = "Enter a number and a message."
            return
        }
        try {
            val sms = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java)
                      else SmsManager.getDefault()
            sms.sendTextMessage(to, null, body, null, null)
            b.composeStatus.text = "Sent."
            b.bodyInput.setText("")
        } catch (e: Exception) {
            b.composeStatus.text = "Could not send: ${e.message}"
        }
    }
}
