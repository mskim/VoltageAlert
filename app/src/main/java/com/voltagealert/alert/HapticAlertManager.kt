package com.voltagealert.alert

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Manages haptic (vibration) alerts.
 *
 * Pattern: 500ms vibrate, 200ms pause, 500ms vibrate, 200ms pause, 500ms vibrate, 800ms pause, repeat
 */
class HapticAlertManager(private val context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    companion object {
        private const val TAG = "HapticAlertManager"

        // Vibration pattern: [delay, vibrate, pause, vibrate, ...]
        private val VIBRATION_PATTERN = longArrayOf(
            0,      // Start immediately
            500,    // Vibrate 500ms
            200,    // Pause 200ms
            500,    // Vibrate 500ms
            200,    // Pause 200ms
            500,    // Vibrate 500ms
            800     // Pause 800ms before repeat
        )

        // Repeat from index 0 (continuous)
        private const val REPEAT_INDEX = 0
    }

    /**
     * Start vibration pattern.
     */
    fun start() {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "Device does not have vibrator")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, REPEAT_INDEX)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATION_PATTERN, REPEAT_INDEX)
            }

            Log.d(TAG, "Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration", e)
        }
    }

    /**
     * Stop vibration.
     */
    fun stop() {
        try {
            vibrator.cancel()
            Log.d(TAG, "Vibration stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop vibration", e)
        }
    }

    /**
     * Check if vibration is active.
     * Note: Can only reliably check on Android S+ (API 31+)
     */
    fun isVibrating(): Boolean {
        return vibrator.hasVibrator()
    }
}
