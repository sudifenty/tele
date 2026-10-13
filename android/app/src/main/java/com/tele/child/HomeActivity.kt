package com.tele.child

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tele.child.databinding.ActivityHomeBinding

/**
 * Launcher screen. The phone is either the parent's phone or the child's phone.
 * Both roles are shown openly; nothing is hidden from the child.
 */
class HomeActivity : AppCompatActivity() {
    private lateinit var b: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A phone that is already linked as a child's phone goes straight to the child screen.
        if (Prefs.isLinked(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnChildRole.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        b.btnParentRole.setOnClickListener {
            startActivity(Intent(this, ParentActivity::class.java))
        }
    }
}
