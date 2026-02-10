package com.voltagealert.alert

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.voltagealert.models.VoltageLevel
import com.voltagealert.ui.AlertActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates all alert modes: visual (full-screen), audio (siren), and haptic (vibration).
 *
 * Manages wake lock to keep screen on during alert.
 * Singleton to ensure alerts are properly stopped across activities.
 */
class AlertCoordinator private constructor(private val context: Context) {
    private val soundGenerator = AlertSoundGenerator()
    private val hapticManager = HapticAlertManager(context)
    private var wakeLock: PowerManager.WakeLock? = null

    // Signal to auto-dismiss AlertActivity when alarm stops programmatically
    private val _shouldDismiss = MutableStateFlow(false)
    val shouldDismiss: StateFlow<Boolean> = _shouldDismiss.asStateFlow()

    companion object {
        private const val TAG = "AlertCoordinator"
        private const val WAKE_LOCK_TAG = "VoltageAlert:AlertWakeLock"

        @Volatile
        private var instance: AlertCoordinator? = null

        fun getInstance(context: Context): AlertCoordinator {
            return instance ?: synchronized(this) {
                instance ?: AlertCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Trigger all alert modes for a dangerous voltage detection.
     *
     * @param voltage The detected voltage level
     */
    fun triggerAlert(voltage: VoltageLevel) {
        if (!voltage.isDangerous) {
            Log.w(TAG, "Alert triggered for non-dangerous voltage: $voltage")
            return
        }

        Log.i(TAG, "Triggering alert for voltage: $voltage")

        // Reset dismiss signal
        _shouldDismiss.value = false

        // Acquire wake lock to keep screen on
        acquireWakeLock()

        // Start audio alert
        soundGenerator.start()

        // Start haptic alert
        hapticManager.start()

        // Start visual alert (full-screen activity)
        val intent = Intent(context, AlertActivity::class.java).apply {
            putExtra(AlertActivity.EXTRA_VOLTAGE_LEVEL, voltage.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    /**
     * Stop all active alerts.
     */
    fun stopAllAlerts() {
        Log.i(TAG, "Stopping all alerts")

        soundGenerator.stop()
        hapticManager.stop()
        releaseWakeLock()

        // Signal AlertActivity to auto-dismiss
        _shouldDismiss.value = true
    }

    /**
     * Check if any alert is currently active.
     */
    fun isAlertActive(): Boolean {
        return soundGenerator.isPlaying() || hapticManager.isVibrating()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            WAKE_LOCK_TAG
        ).apply {
            acquire(10 * 60 * 1000L)  // 10 minutes max
        }

        Log.d(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        stopAllAlerts()
    }
}
