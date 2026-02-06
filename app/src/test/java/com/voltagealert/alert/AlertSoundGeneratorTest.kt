package com.voltagealert.alert

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for AlertSoundGenerator
 * Tests the two-tone siren sound generation
 */
class AlertSoundGeneratorTest {

    @Test
    fun `sound generator can be created`() {
        val generator = AlertSoundGenerator()
        assertNotNull(generator)
    }

    @Test
    fun `sound generator starts and stops`() {
        val generator = AlertSoundGenerator()

        // Start sound
        generator.start()
        assertTrue("Sound should be playing after start", generator.isPlaying())

        // Stop sound
        generator.stop()
        assertFalse("Sound should not be playing after stop", generator.isPlaying())
    }

    @Test
    fun `calling start multiple times is safe`() {
        val generator = AlertSoundGenerator()

        generator.start()
        generator.start()
        generator.start()

        assertTrue("Sound should be playing", generator.isPlaying())

        generator.stop()
    }

    @Test
    fun `calling stop when not playing is safe`() {
        val generator = AlertSoundGenerator()

        // Should not crash when stopping without starting
        generator.stop()
        assertFalse("Sound should not be playing", generator.isPlaying())
    }

    @Test
    fun `stop method properly cleans up`() {
        val generator = AlertSoundGenerator()

        generator.start()
        assertTrue("Sound should be playing", generator.isPlaying())

        generator.stop()
        assertFalse("Sound should be stopped after stop", generator.isPlaying())
    }
}
