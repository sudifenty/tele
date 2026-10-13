package com.tele.child

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tele.child.databinding.ActivityParentBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date

/**
 * Parent dashboard screen (runs on the parent's phone).
 *  - The parent signs in with the email and password used on the web dashboard.
 *  - Shows each linked phone with its latest location, updated live from Firestore.
 *  - Reads only the parent's own family (see firestore.rules).
 *  - Refuses to run on a phone linked as a child's phone, so the child's session is not replaced.
 */
class ParentActivity : AppCompatActivity() {
    private lateinit var b: ActivityParentBinding
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private var devicesReg: ListenerRegistration? = null
    private val locationRegs = mutableMapOf<String, ListenerRegistration>()
    private val devices = linkedMapOf<String, DeviceState>()

    private class DeviceState(
        val label: String,
        val lastSeenMs: Long?,
        var location: Map<String, Any?>?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityParentBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnSignIn.setOnClickListener { signIn() }
        b.btnSignOut.setOnClickListener { signOut() }
    }

    override fun onStart() {
        super.onStart()
        if (Prefs.isLinked(this)) {
            showPanels(signIn = false, dashboard = false)
            status("This phone is linked as a child's phone. Open the parent dashboard on Dad's own phone.")
            return
        }
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null && !user.isAnonymous) {
            loadFamily(user.uid)
        } else {
            showPanels(signIn = true, dashboard = false)
            status("Sign in with the email and password you use on the web dashboard.")
        }
    }

    override fun onStop() {
        stopListening()
        super.onStop()
    }

    private fun signIn() {
        val email = b.email.text.toString().trim()
        val password = b.password.text.toString()
        if (email.isEmpty() || password.isEmpty()) {
            status("Enter your email and password.")
            return
        }
        status("Signing in…")
        lifecycleScope.launch {
            try {
                val uid = FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(email, password).await().user!!.uid
                b.password.setText("")
                loadFamily(uid)
            } catch (e: Exception) {
                showPanels(signIn = true, dashboard = false)
                status("Sign-in failed: ${e.message}")
            }
        }
    }

    private fun loadFamily(uid: String) {
        status("Loading your family…")
        lifecycleScope.launch {
            try {
                val parent = db.collection("parents").document(uid).get().await()
                val familyId = parent.getString("familyId")
                if (familyId == null) {
                    showPanels(signIn = false, dashboard = true)
                    status("No family yet. Create your family on the Tele web dashboard, then open this screen again.")
                    return@launch
                }
                showPanels(signIn = false, dashboard = true)
                startListening(familyId)
            } catch (e: Exception) {
                showPanels(signIn = true, dashboard = false)
                status("Could not load your family: ${e.message}")
            }
        }
    }

    /** Live list of this family's phones, plus each phone's latest location. */
    private fun startListening(familyId: String) {
        stopListening()
        val devicesCol = db.collection("families").document(familyId).collection("devices")
        devicesReg = devicesCol.addSnapshotListener { snap, err ->
            if (err != null) {
                status("Live updates stopped: ${err.message}")
                return@addSnapshotListener
            }
            val docs = snap?.documents ?: emptyList()
            val ids = docs.map { it.id }.toSet()

            // Forget phones that were removed.
            (devices.keys - ids).forEach { id ->
                devices.remove(id)
                locationRegs.remove(id)?.remove()
            }

            for (doc in docs) {
                val previous = devices[doc.id]
                devices[doc.id] = DeviceState(
                    label = doc.getString("label") ?: "Phone",
                    lastSeenMs = doc.getTimestamp("lastSeen")?.toDate()?.time,
                    location = previous?.location
                )
                if (!locationRegs.containsKey(doc.id)) {
                    locationRegs[doc.id] = devicesCol.document(doc.id)
                        .collection("location").document("current")
                        .addSnapshotListener { locSnap, locErr ->
                            if (locErr != null) return@addSnapshotListener
                            devices[doc.id]?.location = locSnap?.data
                            render()
                        }
                }
            }
            render()
        }
    }

    private fun stopListening() {
        devicesReg?.remove()
        devicesReg = null
        locationRegs.values.forEach { it.remove() }
        locationRegs.clear()
        devices.clear()
    }

    private fun render() {
        b.deviceList.removeAllViews()
        if (devices.isEmpty()) {
            status("No linked phones yet. Link one with a code from the web dashboard.")
            return
        }
        status("${devices.size} phone(s). Each phone sends its location every 5 minutes.")

        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        for ((_, d) in devices) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(0xFFF4F7F7.toInt())
            }
            card.addView(TextView(this).apply {
                text = d.label
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            val loc = d.location
            val lat = (loc?.get("lat") as? Number)?.toDouble()
            val lng = (loc?.get("lng") as? Number)?.toDouble()
            val locTs = (loc?.get("ts") as? Number)?.toLong()

            card.addView(TextView(this).apply {
                text = if (lat != null && lng != null) {
                    "Location: %.5f, %.5f".format(lat, lng) +
                        (locTs?.let { "\nUpdated: ${fmt.format(Date(it))}" } ?: "")
                } else {
                    "Location: not shared yet"
                }
                textSize = 14f
            })
            card.addView(TextView(this).apply {
                text = "Last seen: " + (d.lastSeenMs?.let { fmt.format(Date(it)) } ?: "unknown")
                textSize = 13f
            })

            if (lat != null && lng != null) {
                card.addView(Button(this).apply {
                    text = "Open in Maps"
                    setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(d.label)})")))
                    }
                })
            }

            b.deviceList.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) })
        }
    }

    private fun signOut() {
        stopListening()
        FirebaseAuth.getInstance().signOut()
        b.deviceList.removeAllViews()
        showPanels(signIn = true, dashboard = false)
        status("Signed out.")
    }

    private fun showPanels(signIn: Boolean, dashboard: Boolean) {
        b.signInBox.visibility = if (signIn) View.VISIBLE else View.GONE
        b.dashBox.visibility = if (dashboard) View.VISIBLE else View.GONE
    }

    private fun status(message: String) {
        b.statusText.text = message
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
