package com.voltagealert.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voltagealert.R
import com.voltagealert.models.VoltageLevel
import com.voltagealert.ui.AlertActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates all alert modes: visual (full-screen), audio (siren), and haptic (vibration).
 *
 * Uses full-screen intent notification to show AlertActivity, which works on Android 15+
 * where direct startActivity from background/service is blocked.
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
        private const val ALERT_CHANNEL_ID = "VoltageAlertAlarm"
        private const val ALERT_NOTIFICATION_ID = 2001

        @Volatile
        private var instance: AlertCoordinator? = null

        fun getInstance(context: Context): AlertCoordinator {
            return instance ?: synchronized(this) {
                instance ?: AlertCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        createAlertNotificationChannel()
    }

    /**
     * Trigger all alert modes for a dangerous voltage detection.
     *
     * Uses two methods to ensure AlertActivity always shows:
     * 1. Direct startActivity() - works when app is in foreground (all Android versions)
     * 2. Full-screen intent notification - works when screen is off/locked (Android 10+)
     *
     * On Android 15, direct startActivity from service may be blocked,
     * so the notification serves as fallback.
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

        // Acquire wake lock to turn screen on and keep it bright
        acquireWakeLock()

        // Start audio alert
        soundGenerator.start()

        // Start haptic alert
        hapticManager.start()

        // Method 1: Direct activity launch (works when app is in foreground)
        try {
            val intent = Intent(context, AlertActivity::class.java).apply {
                putExtra(AlertActivity.EXTRA_VOLTAGE_LEVEL, voltage.name)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            Log.d(TAG, "Direct activity launch succeeded")
        } catch (e: Exception) {
            Log.w(TAG, "Direct activity launch failed: ${e.message}")
        }

        // Method 2: Full-screen intent notification (fallback for screen off / Android 15)
        showAlertNotification(voltage)
    }

    /**
     * Show a full-screen intent notification that launches AlertActivity.
     * The system will either:
     * - Show AlertActivity full-screen (if phone is locked or screen off)
     * - Show a heads-up notification (if user is actively using the phone)
     */
    private fun showAlertNotification(voltage: VoltageLevel) {
        val fullScreenIntent = Intent(context, AlertActivity::class.java).apply {
            putExtra(AlertActivity.EXTRA_VOLTAGE_LEVEL, voltage.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val voltageText = when (voltage) {
            VoltageLevel.VOLTAGE_220V -> "220V"
            VoltageLevel.VOLTAGE_380V -> "380V"
            VoltageLevel.VOLTAGE_229KV -> "22.9KV"
            VoltageLevel.VOLTAGE_154KV -> "154KV"
            VoltageLevel.VOLTAGE_345KV -> "345KV"
            VoltageLevel.VOLTAGE_765KV -> "765KV"
            else -> voltage.name
        }

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.alert_danger))
            .setContentText(voltageText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)

        Log.d(TAG, "Alert notification posted with full-screen intent for $voltageText")
    }

    /**
     * Stop all active alerts.
     */
    fun stopAllAlerts() {
        Log.i(TAG, "Stopping all alerts")

        soundGenerator.stop()
        hapticManager.stop()
        releaseWakeLock()

        // Cancel the alert notification
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ALERT_NOTIFICATION_ID)

        // Signal AlertActivity to auto-dismiss
        _shouldDismiss.value = true
    }

    /**
     * Check if any alert is currently active.
     */
    fun isAlertActive(): Boolean {
        return soundGenerator.isPlaying() || hapticManager.isVibrating()
    }

    private fun createAlertNotificationChannel() {
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alert_channel_description)
            // No sound on the notification itself - we play our own siren
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
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
