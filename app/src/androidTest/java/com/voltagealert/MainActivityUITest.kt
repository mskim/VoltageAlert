package com.voltagealert

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.voltagealert.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for MainActivity
 * Tests user interface elements and interactions
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityUITest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun mainScreenDisplaysAllElements() {
        // Check that main screen elements are displayed
        onView(withId(R.id.tvConnectionStatus))
            .check(matches(isDisplayed()))

        onView(withId(R.id.tvCurrentVoltage))
            .check(matches(isDisplayed()))

        onView(withId(R.id.rvEventLog))
            .check(matches(isDisplayed()))

        onView(withId(R.id.fabSettings))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testButtonsAreDisplayedAndClickable() {
        // Check all test buttons are displayed
        onView(withId(R.id.btnTest220V))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))

        onView(withId(R.id.btnTest380V))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))

        onView(withId(R.id.btnTest154KV))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))

        onView(withId(R.id.btnTest345KV))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))

        onView(withId(R.id.btnTest765KV))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun clickingSafeVoltageUpdatesDisplay() {
        // Click 220V button (safe voltage)
        onView(withId(R.id.btnTest220V))
            .perform(click())

        // Wait a moment for UI to update
        Thread.sleep(500)

        // Check that voltage is displayed (should show 220V)
        onView(withId(R.id.tvCurrentVoltage))
            .check(matches(withText("220V")))
    }

    @Test
    fun clearLogsButtonIsDisplayed() {
        onView(withId(R.id.btnClearLogs))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun connectionStatusIsDisplayed() {
        onView(withId(R.id.tvConnectionStatus))
            .check(matches(isDisplayed()))

        onView(withId(R.id.statusIndicator))
            .check(matches(isDisplayed()))
    }
}
