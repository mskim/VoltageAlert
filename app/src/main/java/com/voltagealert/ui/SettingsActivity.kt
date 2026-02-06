package com.voltagealert.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.voltagealert.R

/**
 * Settings activity for user preferences.
 *
 * TODO: Implement preference fragments for:
 * - Sound/vibration toggles
 * - Mock sensor toggle (debug)
 * - Device address configuration
 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
