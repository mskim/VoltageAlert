package com.voltagealert

import com.voltagealert.logging.DuplicateSuppressionFilter
import com.voltagealert.models.VoltageLevel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DuplicateSuppressionFilter.
 *
 * Tests the core duplicate suppression logic:
 * - First 3 identical readings should be logged
 * - 4th+ identical reading should be suppressed
 * - Different voltage resets the counter
 */
class DuplicateSuppressionFilterTest {
    private lateinit var filter: DuplicateSuppressionFilter

    @Before
    fun setup() {
        filter = DuplicateSuppressionFilter()
    }

    @Test
    fun `first three identical readings should be logged`() {
        val voltage = VoltageLevel.VOLTAGE_220V

        // First three should all return true (should log)
        assertTrue("First reading should be logged", filter.shouldLogReading(voltage))
        assertTrue("Second reading should be logged", filter.shouldLogReading(voltage))
        assertTrue("Third reading should be logged", filter.shouldLogReading(voltage))
    }

    @Test
    fun `fourth identical reading should be suppressed`() {
        val voltage = VoltageLevel.VOLTAGE_220V

        // First three logged
        repeat(3) { filter.shouldLogReading(voltage) }

        // Fourth should be suppressed
        assertFalse("Fourth reading should be suppressed", filter.shouldLogReading(voltage))
    }

    @Test
    fun `subsequent identical readings should remain suppressed`() {
        val voltage = VoltageLevel.VOLTAGE_220V

        // First three logged
        repeat(3) { filter.shouldLogReading(voltage) }

        // 4th through 10th should all be suppressed
        repeat(7) {
            assertFalse(
                "Reading ${it + 4} should be suppressed",
                filter.shouldLogReading(voltage)
            )
        }
    }

    @Test
    fun `different voltage should reset suppression`() {
        val voltage1 = VoltageLevel.VOLTAGE_220V
        val voltage2 = VoltageLevel.VOLTAGE_380V

        // First three 220V logged
        repeat(3) { filter.shouldLogReading(voltage1) }

        // Fourth 220V suppressed
        assertFalse(filter.shouldLogReading(voltage1))

        // Different voltage (380V) should be logged
        assertTrue("Different voltage should be logged", filter.shouldLogReading(voltage2))

        // Next 220V should be logged (counter reset)
        assertTrue("220V after different voltage should be logged", filter.shouldLogReading(voltage1))
    }

    @Test
    fun `alternating voltages should never be suppressed`() {
        val voltage1 = VoltageLevel.VOLTAGE_220V
        val voltage2 = VoltageLevel.VOLTAGE_380V

        // Alternate between two voltages
        repeat(10) {
            val voltage = if (it % 2 == 0) voltage1 else voltage2
            assertTrue(
                "Alternating reading $it should be logged",
                filter.shouldLogReading(voltage)
            )
        }
    }

    @Test
    fun `reset should clear filter state`() {
        val voltage = VoltageLevel.VOLTAGE_220V

        // Build up to suppression
        repeat(4) { filter.shouldLogReading(voltage) }

        // Should be suppressed now
        assertFalse(filter.shouldLogReading(voltage))

        // Reset
        filter.reset()

        // Should log again after reset
        assertTrue("After reset, should log again", filter.shouldLogReading(voltage))
    }

    @Test
    fun `window size should not exceed max size`() {
        val voltage = VoltageLevel.VOLTAGE_220V

        // Add 15 readings (more than window size of 10)
        repeat(15) { filter.shouldLogReading(voltage) }

        // Window size should be capped at 10
        assertEquals("Window size should be capped", 10, filter.getWindowSize())
    }

    @Test
    fun `mixed voltage pattern should suppress correctly`() {
        val v220 = VoltageLevel.VOLTAGE_220V
        val v380 = VoltageLevel.VOLTAGE_380V

        // Pattern: 220, 220, 220, 220 (4th suppressed)
        assertTrue(filter.shouldLogReading(v220))
        assertTrue(filter.shouldLogReading(v220))
        assertTrue(filter.shouldLogReading(v220))
        assertFalse(filter.shouldLogReading(v220))

        // Add 380 (resets)
        assertTrue(filter.shouldLogReading(v380))

        // Pattern: 380, 380, 380 (all logged, only 3 total)
        assertTrue(filter.shouldLogReading(v380))
        assertTrue(filter.shouldLogReading(v380))

        // 4th 380 should be suppressed
        assertFalse(filter.shouldLogReading(v380))
    }
}
