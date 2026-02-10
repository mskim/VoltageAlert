package com.voltagealert.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.util.Locale
import com.voltagealert.R
import com.voltagealert.alert.AlertCoordinator
import com.voltagealert.databinding.ActivityAlertBinding
import com.voltagealert.models.VoltageLevel
import kotlinx.coroutines.launch

/**
 * Full-screen alert activity shown when dangerous voltage is detected.
 *
 * Features:
 * - Displays voltage-specific warning image
 * - Shows on lock screen and turns screen on
 * - Coordinates with AlertCoordinator to trigger audio/haptic alerts
 * - Provides dismiss button to stop all alerts
 */
class AlertActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlertBinding
    private lateinit var alertCoordinator: AlertCoordinator

    companion object {
        const val EXTRA_VOLTAGE_LEVEL = "voltage_level"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateBaseContextLocale(newBase, "ko"))
    }

    private fun updateBaseContextLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set flags to show on lock screen and turn screen on
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alertCoordinator = AlertCoordinator.getInstance(applicationContext)

        // Get voltage level from intent
        val voltageLevelName = intent.getStringExtra(EXTRA_VOLTAGE_LEVEL)
        val voltageLevel = voltageLevelName?.let {
            try {
                VoltageLevel.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        if (voltageLevel != null) {
            setupAlert(voltageLevel)
        } else {
            // Invalid voltage level, close activity
            finish()
        }

        // Disable back button (force intentional dismissal via OK button)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - prevent back button dismissal for safety
            }
        })

        // Dismiss button
        binding.btnDismiss.setOnClickListener {
            dismissAlert()
        }

        // Auto-dismiss when AlertCoordinator signals (sensor stopped sending)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                alertCoordinator.shouldDismiss.collect { shouldDismiss ->
                    if (shouldDismiss) {
                        Log.d("AlertActivity", "Auto-dismissing (sensor stopped sending)")
                        dismissAlert()
                    }
                }
            }
        }
    }

    private fun setupAlert(voltage: VoltageLevel) {
        // Set title (get string resource)
        binding.tvAlertTitle.text = getString(R.string.alert_danger)

        // Set warning image (use detection image)
        binding.ivWarningImage.setImageResource(voltage.detectionImageRes)

        // Warning text is already set in XML
    }

    private fun dismissAlert() {
        Log.d("AlertActivity", "dismissAlert called")

        // Stop all alerts (sound, vibration)
        alertCoordinator.stopAllAlerts()
        Log.d("AlertActivity", "stopAllAlerts completed")

        // Clear window flags that might prevent closing
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d("AlertActivity", "Window flags cleared")

        // Close this activity and return to MainActivity
        Log.d("AlertActivity", "Calling finish()")
        finish()
        Log.d("AlertActivity", "finish() called")
    }

    override fun onDestroy() {
        Log.d("AlertActivity", "onDestroy called")
        super.onDestroy()
        alertCoordinator.cleanup()
        Log.d("AlertActivity", "onDestroy completed")
    }
}
