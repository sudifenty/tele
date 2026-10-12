package com.tele.child

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tele.child.databinding.ActivityLogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shows the child exactly what the parent can see, read from the same sources. */
class LogActivity : AppCompatActivity() {
    private data class Row(val ts: Long, val text: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityLogBinding.inflate(layoutInflater)
        setContentView(b.root)

        val isDefault = SmsRole.isDefault(this)
        b.logHeader.text = if (isDefault)
            "Tele is your default SMS app. Texts show content."
        else
            "Tele is NOT your default SMS app. Texts show sender and time only."

        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                val fmt = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
                val calls = CallLogReader.read(this@LogActivity, null, 100).map { c ->
                    val who = c.name ?: c.number
                    val dur = if (c.durationSec > 0) " · ${c.durationSec / 60}m ${c.durationSec % 60}s" else ""
                    Row(c.ts, "CALL · ${c.type} · $who$dur · ${fmt.format(Date(c.ts))}")
                }
                val sms = SmsReader.read(this@LogActivity, null, 100, includeBody = isDefault).map { m ->
                    val who = m.name ?: m.address
                    val content = m.body ?: "[text content hidden: Tele is not the default SMS app]"
                    Row(m.ts, "TEXT · ${m.folder} · $who · ${fmt.format(Date(m.ts))}\n$content")
                }
                (calls + sms).sortedByDescending { it.ts }
            }
            b.logList.adapter = ArrayAdapter(
                this@LogActivity,
                android.R.layout.simple_list_item_1,
                rows.map { it.text }
            )
        }
    }
}
