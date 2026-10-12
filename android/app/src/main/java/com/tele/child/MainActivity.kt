package com.tele.child

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.tele.child.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshUi() }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshUi() }

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) requestBackgroundLocation()   // second system dialog: "Allow all the time"
        refreshUi()
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshUi() }

    private val sosLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> sendSos(withLocation = granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnConsent.setOnClickListener { showConsent() }
        b.btnPermissions.setOnClickListener { requestSetupPermissions() }
        b.btnLocation.setOnClickListener { requestLocation() }
        b.btnDefaultSms.setOnClickListener { explainDefaultSms() }
        b.btnPair.setOnClickListener {
            if (!Prefs.consentAccepted(this)) showConsent()
            else startActivity(Intent(this, PairActivity::class.java))
        }
        b.btnLog.setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }
        b.btnSos.setOnClickListener { confirmSos() }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val consent = Prefs.consentAccepted(this)
        val linked = Prefs.isLinked(this)
        val allowed = Permissions.SETUP.count { Permissions.granted(this, it) }
        val isDefaultSms = SmsRole.isDefault(this)
        val notificationsOn = NotificationManagerCompat.from(this).areNotificationsEnabled()

        b.supervisionBanner.text = when {
            linked && consent -> getString(com.tele.child.R.string.supervision_notice)
            else -> "Tele is not supervising this phone yet. Nothing is shared until you link to a parent."
        }

        val locationText = when {
            Permissions.hasLocationAlways(this) -> "on (shared in real time while supervision is on)"
            Permissions.hasLocation(this) -> "partly: allow \"Allow all the time\" for real-time sharing"
            else -> "off (not allowed)"
        }

        b.statusText.text = buildString {
            appendLine("Consent to supervision: ${if (consent) "yes" else "not yet"}")
            appendLine("Permissions allowed: $allowed of ${Permissions.SETUP.size}")
            appendLine("Location: $locationText")
            appendLine(
                "Texts: " + if (isDefaultSms) "Tele is the default SMS app, so text content is shared with your parent."
                else "Tele is not the default SMS app, so your parent sees only sender and time for texts."
            )
            appendLine("Linked to a parent: ${if (linked) "yes" else "no"}")
            append("Notifications: ${if (notificationsOn) "on" else "OFF. Turn them on so the supervision notice is visible."}")
        }

        if (linked && consent) {
            SupervisionService.start(this)
            LocationService.start(this)
        }
    }

    private fun showConsent() {
        AlertDialog.Builder(this)
            .setTitle("What Tele shares")
            .setMessage(
                "If you link this phone to Dad, Tele will:\n\n" +
                "• Share your call log: number, name if saved, time, length, and whether each call was incoming, outgoing or missed.\n\n" +
                "• Share your texts' sender, time and folder.\n\n" +
                "• Share text content ONLY while Tele is your default SMS app.\n\n" +
                "• Share your location in real time while supervision is on. A separate notice stays on your phone while it is shared, and Dad can pause sharing from the dashboard.\n\n" +
                "• Keep a notice in your notifications while supervision is on.\n\n" +
                "Tele never records calls, never listens to audio, never hides its icon, and never forwards calls.\n\n" +
                "Tele does not work with WhatsApp."
            )
            .setPositiveButton("I understand") { _, _ ->
                Prefs.setConsentAccepted(this)
                refreshUi()
                Toast.makeText(this, "Next: allow the permissions.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun requestSetupPermissions() {
        if (!Prefs.consentAccepted(this)) {
            showConsent()
            return
        }
        permissionLauncher.launch(Permissions.SETUP.toTypedArray())
    }

    private fun requestLocation() {
        if (!Prefs.consentAccepted(this)) {
            showConsent()
            return
        }
        if (Permissions.hasLocationAlways(this)) {
            Toast.makeText(this, "Location is already allowed.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Share location with Dad?")
            .setMessage(
                "Android will ask you for location access. Choose \"Allow all the time\" so Tele can share your location " +
                "when the app is not open. A notice will stay on your phone while your location is shared. " +
                "Dad can pause sharing from the dashboard."
            )
            .setPositiveButton("Continue") { _, _ ->
                if (Permissions.hasLocation(this)) requestBackgroundLocation()
                else locationLauncher.launch(Permissions.LOCATION)
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Permissions.hasLocationAlways(this)) {
            backgroundLocationLauncher.launch(Permissions.BACKGROUND_LOCATION)
        }
    }

    private fun explainDefaultSms() {
        if (!Prefs.consentAccepted(this)) {
            showConsent()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Set Tele as default SMS app?")
            .setMessage(
                "Android will ask you to confirm.\n\n" +
                "While Tele is the default SMS app, Dad can see the content of your texts. " +
                "Tele replaces your usual messaging app while it is the default. " +
                "You can change the default back in Android Settings at any time, and then Dad will see only sender and time."
            )
            .setPositiveButton("Continue") { _, _ ->
                roleLauncher.launch(SmsRole.requestIntent(this))
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun confirmSos() {
        AlertDialog.Builder(this)
            .setTitle("Send SOS to Dad?")
            .setMessage(
                "Dad will be alerted now, with your current location if you allow it. " +
                "Tele shares your location once for this SOS."
            )
            .setPositiveButton("Send SOS") { _, _ ->
                if (Permissions.hasLocation(this)) sendSos(withLocation = true)
                else sosLocationLauncher.launch(Permissions.LOCATION)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendSos(withLocation: Boolean) {
        if (!Prefs.isLinked(this)) {
            Toast.makeText(this, "Link to a parent first.", Toast.LENGTH_LONG).show()
            return
        }
        b.btnSos.isEnabled = false
        lifecycleScope.launch {
            try {
                val location = if (withLocation) Sos.currentLocation(this@MainActivity) else null
                Repo.sendSos(
                    this@MainActivity,
                    location?.latitude, location?.longitude, location?.accuracy
                )
                val phone = runCatching { Repo.sosContactPhone(this@MainActivity) }.getOrNull()
                if (phone != null && Permissions.granted(this@MainActivity, android.Manifest.permission.SEND_SMS)) {
                    runCatching { Sos.textEmergencyContact(this@MainActivity, phone, location) }
                }
                val msg = if (location != null) "SOS sent to Dad with your location."
                          else "SOS sent to Dad without location. In danger, call 999 or 112."
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "SOS could not be sent. In danger, call 999 or 112.", Toast.LENGTH_LONG).show()
            } finally {
                b.btnSos.isEnabled = true
            }
        }
    }
}
