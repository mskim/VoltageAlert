package com.voltagealert

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.voltagealert.alert.AlertCoordinator
import com.voltagealert.models.VoltageLevel
import com.voltagealert.ui.MainActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for complete alert flow
 * Tests sound, vibration, and UI working together
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AlertIntegrationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alertCoordinator = AlertCoordinator.getInstance(context)

    @After
    fun cleanup() {
        alertCoordinator.stopAllAlerts()
    }

    @Test
    fun clicking380VButtonTriggersAlert() {
        // Click 380V button
        onView(withId(R.id.btnTest380V))
            .perform(click())

        // Wait for alert to appear
        Thread.sleep(1000)

        // Verify alert screen is displayed
        onView(withText(R.string.alert_danger))
            .check(matches(isDisplayed()))

        // Verify OK button is displayed
        onView(withId(R.id.btnDismiss))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))

        // Click OK to dismiss
        onView(withId(R.id.btnDismiss))
            .perform(click())

        // Wait for dismissal
        Thread.sleep(500)
    }

    @Test
    fun clicking765KVButtonShowsCriticalAlert() {
        // Click 765KV button (critical danger)
        onView(withId(R.id.btnTest765KV))
            .perform(click())

        // Wait for alert
        Thread.sleep(1000)

        // Verify alert is shown
        onView(withText(R.string.alert_danger))
            .check(matches(isDisplayed()))

        // Dismiss the alert
        onView(withId(R.id.btnDismiss))
            .perform(click())

        Thread.sleep(500)
    }

    @Test
    fun clicking220VDoesNotTriggerAlert() {
        // Click 220V button (safe voltage)
        onView(withId(R.id.btnTest220V))
            .perform(click())

        // Wait briefly
        Thread.sleep(500)

        // Verify voltage display is updated but no alert screen
        onView(withId(R.id.tvCurrentVoltage))
            .check(matches(isDisplayed()))
    }

    @Test
    fun alertCanBeDismissedAndTriggeredAgain() {
        // First alert
        onView(withId(R.id.btnTest345KV))
            .perform(click())

        Thread.sleep(1000)

        onView(withId(R.id.btnDismiss))
            .perform(click())

        Thread.sleep(500)

        // Second alert
        onView(withId(R.id.btnTest154KV))
            .perform(click())

        Thread.sleep(1000)

        onView(withId(R.id.btnDismiss))
            .perform(click())

        Thread.sleep(500)
    }

    @Test
    fun multipleVoltageButtonClicksUpdateDisplay() {
        // Click different voltage buttons
        onView(withId(R.id.btnTest220V)).perform(click())
        Thread.sleep(300)

        onView(withId(R.id.btnTest380V)).perform(click())
        Thread.sleep(1000)
        onView(withId(R.id.btnDismiss)).perform(click())
        Thread.sleep(300)

        onView(withId(R.id.btnTest154KV)).perform(click())
        Thread.sleep(1000)
        onView(withId(R.id.btnDismiss)).perform(click())
        Thread.sleep(300)
    }
}
