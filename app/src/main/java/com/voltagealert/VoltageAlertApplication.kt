package com.voltagealert

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.voltagealert.logging.VoltageLogDatabase
import java.util.Locale

/**
 * Application class for VoltageAlert.
 *
 * Initializes:
 * - Room database
 * - Notification channels
 */
class VoltageAlertApplication : Application() {
    companion object {
        private const val SERVICE_CHANNEL_ID = "VoltageAlertService"
        private const val ALERT_CHANNEL_ID = "VoltageAlertAlerts"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(updateBaseContextLocale(base, "ko"))
    }

    override fun onCreate() {
        super.onCreate()

        // Set Korean as default locale
        setLocale(this, "ko")

        // Initialize database
        VoltageLogDatabase.getInstance(this)

        // Create notification channels
        createNotificationChannels()
    }

    private fun updateBaseContextLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    private fun setLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Service channel (low importance)
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.service_channel_description)
                setShowBadge(false)
            }

            // Alert channel (high importance)
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Voltage Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical high voltage warnings"
                setShowBadge(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }
}
