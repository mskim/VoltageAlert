package com.voltagealert.models

import java.time.LocalDateTime

/**
 * Represents a single voltage reading from the Bluetooth sensor.
 *
 * @param voltage The detected voltage level
 * @param timestamp When the reading was received
 * @param sequenceNumber Packet sequence number from sensor (for duplicate detection)
 * @param rawBytes Original 10-byte packet data
 */
data class VoltageReading(
    val voltage: VoltageLevel,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val sequenceNumber: Int,
    val rawBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VoltageReading

        if (voltage != other.voltage) return false
        if (timestamp != other.timestamp) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (rawBytes != null) {
            if (other.rawBytes == null) return false
            if (!rawBytes.contentEquals(other.rawBytes)) return false
        } else if (other.rawBytes != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = voltage.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + sequenceNumber
        result = 31 * result + (rawBytes?.contentHashCode() ?: 0)
        return result
    }
}
