package com.tele.child

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tele.child.databinding.ActivityPairBinding
import kotlinx.coroutines.launch

class PairActivity : AppCompatActivity() {
    private lateinit var b: ActivityPairBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPairBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnLink.setOnClickListener {
            if (!Prefs.consentAccepted(this)) {
                b.pairStatus.text = "Read \"What Tele shares\" on the main screen first."
                return@setOnClickListener
            }
            val code = b.codeInput.text.toString().trim()
            if (!Regex("^\\d{6}$").matches(code)) {
                b.pairStatus.text = "Enter the 6-digit code from the parent dashboard."
                return@setOnClickListener
            }
            b.btnLink.isEnabled = false
            b.pairStatus.text = "Linking..."
            lifecycleScope.launch {
                try {
                    Repo.linkDevice(this@PairActivity, code)
                    SupervisionService.start(this@PairActivity)
                    Toast.makeText(this@PairActivity, "Linked. Supervision is on.", Toast.LENGTH_LONG).show()
                    finish()
                } catch (e: Exception) {
                    b.pairStatus.text = e.message ?: "Could not link this phone."
                    b.btnLink.isEnabled = true
                }
            }
        }
    }
}
