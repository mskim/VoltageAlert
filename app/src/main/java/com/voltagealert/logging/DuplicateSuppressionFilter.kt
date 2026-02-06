package com.voltagealert.logging

import com.voltagealert.models.VoltageLevel
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Filters duplicate voltage readings to prevent log spam.
 *
 * Rule: Suppress log entries if the same voltage appears more than 3 times consecutively.
 *
 * Implementation: Maintains a sliding window of recent readings. If the last 4 readings
 * are all identical, subsequent identical readings are suppressed until a different voltage arrives.
 */
class DuplicateSuppressionFilter {
    private val recentReadings = ConcurrentLinkedQueue<VoltageLevel>()
    private val maxWindowSize = 10

    /**
     * Check if a reading should be logged (not suppressed).
     *
     * @param voltage The voltage level to check
     * @return true if should log, false if should suppress
     */
    fun shouldLogReading(voltage: VoltageLevel): Boolean {
        // Add to window
        recentReadings.offer(voltage)

        // Trim window to max size
        while (recentReadings.size > maxWindowSize) {
            recentReadings.poll()
        }

        // Always log first 3 readings
        if (recentReadings.size < 4) {
            return true
        }

        // Check if last 4 readings are all identical
        val lastFour = recentReadings.toList().takeLast(4)

        // If all 4 are the same as current voltage, suppress (this is the 4th+ occurrence)
        return !lastFour.all { it == voltage }
    }

    /**
     * Reset the filter state (clear history).
     */
    fun reset() {
        recentReadings.clear()
    }

    /**
     * Get current window size for testing.
     */
    fun getWindowSize(): Int = recentReadings.size
}
