package com.voltagealert.models

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for VoltageLevel enum
 * Tests voltage detection and danger classification
 */
class VoltageLevelTest {

    @Test
    fun `220V is not dangerous`() {
        assertFalse("220V should be safe", VoltageLevel.VOLTAGE_220V.isDangerous)
    }

    @Test
    fun `high voltage levels are dangerous`() {
        assertTrue("380V should be dangerous", VoltageLevel.VOLTAGE_380V.isDangerous)
        assertTrue("154KV should be dangerous", VoltageLevel.VOLTAGE_154KV.isDangerous)
        assertTrue("229KV should be dangerous", VoltageLevel.VOLTAGE_229KV.isDangerous)
        assertTrue("345KV should be dangerous", VoltageLevel.VOLTAGE_345KV.isDangerous)
        assertTrue("500KV should be dangerous", VoltageLevel.VOLTAGE_500KV.isDangerous)
        assertTrue("765KV should be dangerous", VoltageLevel.VOLTAGE_765KV.isDangerous)
    }

    @Test
    fun `diagnostic levels are not dangerous`() {
        assertFalse("Diagnostic OK should not be dangerous", VoltageLevel.DIAGNOSTIC_OK.isDangerous)
        assertFalse("Diagnostic NG should not be dangerous", VoltageLevel.DIAGNOSTIC_NG.isDangerous)
    }

    @Test
    fun `voltage levels can be found by byte code`() {
        assertEquals(VoltageLevel.VOLTAGE_220V, VoltageLevel.fromByteCode(0x01))
        assertEquals(VoltageLevel.VOLTAGE_380V, VoltageLevel.fromByteCode(0x02))
        assertEquals(VoltageLevel.VOLTAGE_229KV, VoltageLevel.fromByteCode(0x03))
        assertEquals(VoltageLevel.VOLTAGE_154KV, VoltageLevel.fromByteCode(0x04))
        assertEquals(VoltageLevel.VOLTAGE_345KV, VoltageLevel.fromByteCode(0x05))
        assertEquals(VoltageLevel.VOLTAGE_500KV, VoltageLevel.fromByteCode(0x06))
        assertEquals(VoltageLevel.VOLTAGE_765KV, VoltageLevel.fromByteCode(0x07))
    }

    @Test
    fun `invalid byte code returns null`() {
        assertNull("Invalid byte code should return null", VoltageLevel.fromByteCode(0xFF.toByte()))
        assertNull("Invalid byte code should return null", VoltageLevel.fromByteCode(0x99.toByte()))
    }

    @Test
    fun `dangerous levels list contains all dangerous voltages`() {
        val dangerousLevels = VoltageLevel.dangerousLevels()

        assertTrue("Should contain 380V", dangerousLevels.contains(VoltageLevel.VOLTAGE_380V))
        assertTrue("Should contain 154KV", dangerousLevels.contains(VoltageLevel.VOLTAGE_154KV))
        assertTrue("Should contain 229KV", dangerousLevels.contains(VoltageLevel.VOLTAGE_229KV))
        assertTrue("Should contain 345KV", dangerousLevels.contains(VoltageLevel.VOLTAGE_345KV))
        assertTrue("Should contain 500KV", dangerousLevels.contains(VoltageLevel.VOLTAGE_500KV))
        assertTrue("Should contain 765KV", dangerousLevels.contains(VoltageLevel.VOLTAGE_765KV))

        assertFalse("Should not contain 220V", dangerousLevels.contains(VoltageLevel.VOLTAGE_220V))
        assertFalse("Should not contain DIAGNOSTIC_OK", dangerousLevels.contains(VoltageLevel.DIAGNOSTIC_OK))
    }

    @Test
    fun `all voltage levels have valid resources`() {
        VoltageLevel.entries.forEach { voltage ->
            assertTrue("${voltage.name} should have positive display name resource", voltage.displayNameRes > 0)
            assertTrue("${voltage.name} should have positive setting image resource", voltage.settingImageRes > 0)
            assertTrue("${voltage.name} should have positive detection image resource", voltage.detectionImageRes > 0)
            assertTrue("${voltage.name} should have positive detection inverted image resource",
                voltage.detectionInvertedImageRes > 0)
        }
    }
}
