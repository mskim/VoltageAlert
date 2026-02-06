package com.voltagealert.testing

import com.voltagealert.bluetooth.SensorDataParser
import com.voltagealert.models.VoltageLevel
import com.voltagealert.models.VoltageReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Mock Bluetooth device for testing without physical sensor hardware.
 *
 * Generates simulated voltage readings with various test scenarios.
 */
object MockBluetoothDevice {

    /**
     * Test scenarios for different use cases.
     */
    enum class Scenario {
        /** Only safe diagnostic readings */
        SAFE,

        /** Random dangerous voltages */
        DANGER,

        /** Mix of safe and dangerous */
        MIXED,

        /** Same voltage repeated (for testing duplicate suppression) */
        DUPLICATE_TEST,

        /** Cycling through all voltage levels */
        ALL_VOLTAGES
    }

    /**
     * Generate a flow of simulated voltage readings.
     *
     * @param scenario The test scenario to simulate
     * @param intervalMs Delay between readings in milliseconds
     * @return Flow of VoltageReading
     */
    fun generateReadings(
        scenario: Scenario = Scenario.MIXED,
        intervalMs: Long = 2000
    ): Flow<VoltageReading> = flow {
        var sequenceNumber = 0

        when (scenario) {
            Scenario.SAFE -> {
                while (true) {
                    val reading = createReading(VoltageLevel.DIAGNOSTIC_OK, sequenceNumber++)
                    emit(reading)
                    delay(intervalMs)
                }
            }

            Scenario.DANGER -> {
                val dangerousLevels = VoltageLevel.dangerousLevels()
                while (true) {
                    val voltage = dangerousLevels.random()
                    val reading = createReading(voltage, sequenceNumber++)
                    emit(reading)
                    delay(intervalMs)
                }
            }

            Scenario.MIXED -> {
                val allLevels = VoltageLevel.entries
                while (true) {
                    val voltage = allLevels.random()
                    val reading = createReading(voltage, sequenceNumber++)
                    emit(reading)
                    delay(intervalMs)
                }
            }

            Scenario.DUPLICATE_TEST -> {
                // Send 220V 10 times to test duplicate suppression
                val voltage = VoltageLevel.VOLTAGE_220V
                repeat(10) {
                    val reading = createReading(voltage, sequenceNumber++)
                    emit(reading)
                    delay(intervalMs)
                }

                // Then switch to different voltage
                val reading = createReading(VoltageLevel.VOLTAGE_380V, sequenceNumber++)
                emit(reading)
                delay(intervalMs)

                // Continue with mixed
                val allLevels = VoltageLevel.entries
                while (true) {
                    val randomVoltage = allLevels.random()
                    val randomReading = createReading(randomVoltage, sequenceNumber++)
                    emit(randomReading)
                    delay(intervalMs)
                }
            }

            Scenario.ALL_VOLTAGES -> {
                while (true) {
                    VoltageLevel.entries.forEach { voltage ->
                        val reading = createReading(voltage, sequenceNumber++)
                        emit(reading)
                        delay(intervalMs)
                    }
                }
            }
        }
    }

    /**
     * Create a valid VoltageReading with properly formatted packet.
     */
    private fun createReading(voltage: VoltageLevel, sequenceNumber: Int): VoltageReading {
        val packet = SensorDataParser.createTestPacket(voltage, sequenceNumber)
        return SensorDataParser.parsePacket(packet)
            ?: throw IllegalStateException("Failed to parse test packet")
    }

    /**
     * Generate a single random reading for testing.
     */
    fun generateSingleReading(sequenceNumber: Int = 0): VoltageReading {
        val voltage = VoltageLevel.entries.random()
        return createReading(voltage, sequenceNumber)
    }

    /**
     * Generate an invalid packet for error testing.
     */
    fun generateInvalidPacket(): ByteArray {
        return ByteArray(10) { Random.nextInt(0, 256).toByte() }
    }
}
