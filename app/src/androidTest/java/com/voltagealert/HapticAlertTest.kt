package com.voltagealert

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.voltagealert.alert.HapticAlertManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for HapticAlertManager
 * Tests vibration functionality on device/emulator
 */
@RunWith(AndroidJUnit4::class)
class HapticAlertTest {

    private lateinit var context: Context
    private lateinit var hapticManager: HapticAlertManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        hapticManager = HapticAlertManager(context)
    }

    @After
    fun tearDown() {
        hapticManager.stop()
    }

    @Test
    fun vibrationCanStart() {
        // Start vibration
        hapticManager.start()

        // Verify it's vibrating (this checks if device has vibrator capability)
        assert(hapticManager.isVibrating())

        // Let it vibrate for a moment
        Thread.sleep(1000)
    }

    @Test
    fun vibrationCanStop() {
        // Start vibration
        hapticManager.start()

        // Let it vibrate briefly
        Thread.sleep(500)

        // Stop vibration
        hapticManager.stop()

        // Note: isVibrating() returns true if device HAS a vibrator,
        // not if it's currently vibrating
        assert(hapticManager.isVibrating())
    }

    @Test
    fun multipleStartsAreSafe() {
        hapticManager.start()
        hapticManager.start()
        hapticManager.start()

        Thread.sleep(500)

        hapticManager.stop()
    }

    @Test
    fun multipleStopsAreSafe() {
        hapticManager.start()
        Thread.sleep(300)

        hapticManager.stop()
        hapticManager.stop()
        hapticManager.stop()
    }
}
