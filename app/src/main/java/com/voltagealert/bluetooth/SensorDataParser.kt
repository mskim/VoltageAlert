package com.voltagealert.bluetooth

import com.voltagealert.models.VoltageLevel
import com.voltagealert.models.VoltageReading
import java.time.LocalDateTime

/**
 * Parser for voltage sensor Bluetooth packets.
 *
 * Packet Format (10 bytes):
 * [0xAA][VoltageCode][SeqHi][SeqLo][CRC8][Padding...][0x55]
 *
 * Byte 0: Header (0xAA)
 * Byte 1: Voltage code (maps to VoltageLevel)
 * Bytes 2-3: Sequence number (big-endian uint16)
 * Byte 4: CRC8 checksum (XOR of bytes 1-3)
 * Bytes 5-8: Reserved/padding
 * Byte 9: Footer (0x55)
 */
object SensorDataParser {
    private const val PACKET_SIZE = 10
    private const val HEADER_BYTE: Byte = 0xAA.toByte()
    private const val FOOTER_BYTE: Byte = 0x55.toByte()

    /**
     * Parse a Bluetooth packet into a VoltageReading.
     *
     * Supports two formats:
     * 1. ASCII text: "220V WARNING", "380V WARNING", "154KV WARNING", etc.
     * 2. Binary: [0xAA][VoltageCode][SeqHi][SeqLo][CRC8][Padding...][0x55]
     *
     * @param data The raw packet data
     * @return VoltageReading if valid, null if invalid/corrupted
     */
    fun parsePacket(data: ByteArray): VoltageReading? {
        // Try ASCII format first (actual format from ST9401-UP device)
        val asciiReading = parseAsciiPacket(data)
        if (asciiReading != null) {
            return asciiReading
        }

        // Fall back to binary format (for future compatibility)
        return parseBinaryPacket(data)
    }

    /**
     * Parse ASCII text format: "220V WARNING", "380V WARNING", etc.
     */
    private fun parseAsciiPacket(data: ByteArray): VoltageReading? {
        try {
            // Convert bytes to ASCII string
            val text = data.toString(Charsets.US_ASCII).trim()

            // Extract voltage value (e.g., "220V", "380V", "154KV", "22.9KV")
            val voltagePattern = Regex("""(\d+\.?\d*)(V|KV)""")
            val match = voltagePattern.find(text) ?: return null

            val value = match.groupValues[1]
            val unit = match.groupValues[2]

            // Map to VoltageLevel
            val voltage = when {
                value == "220" && unit == "V" -> VoltageLevel.VOLTAGE_220V
                value == "380" && unit == "V" -> VoltageLevel.VOLTAGE_380V
                value == "154" && unit == "KV" -> VoltageLevel.VOLTAGE_154KV
                (value == "229" || value == "22.9") && unit == "KV" -> VoltageLevel.VOLTAGE_229KV
                value == "345" && unit == "KV" -> VoltageLevel.VOLTAGE_345KV
                value == "500" && unit == "KV" -> VoltageLevel.VOLTAGE_500KV
                value == "765" && unit == "KV" -> VoltageLevel.VOLTAGE_765KV
                else -> return null
            }

            return VoltageReading(
                voltage = voltage,
                timestamp = LocalDateTime.now(),
                sequenceNumber = 0, // ASCII format doesn't include sequence number
                rawBytes = data.copyOf()
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Parse binary packet format (legacy/future compatibility).
     */
    private fun parseBinaryPacket(data: ByteArray): VoltageReading? {
        // Validate packet size
        if (data.size != PACKET_SIZE) {
            return null
        }

        // Validate header and footer
        if (data[0] != HEADER_BYTE || data[9] != FOOTER_BYTE) {
            return null
        }

        // Extract fields
        val voltageCode = data[1]
        val seqHi = data[2].toInt() and 0xFF
        val seqLo = data[3].toInt() and 0xFF
        val receivedCrc = data[4]

        // Calculate expected CRC (XOR of bytes 1-3)
        val calculatedCrc = (data[1].toInt() xor data[2].toInt() xor data[3].toInt()).toByte()

        // Validate CRC
        if (receivedCrc != calculatedCrc) {
            return null
        }

        // Map voltage code to VoltageLevel
        val voltage = VoltageLevel.fromByteCode(voltageCode) ?: return null

        // Combine sequence number (big-endian)
        val sequenceNumber = (seqHi shl 8) or seqLo

        return VoltageReading(
            voltage = voltage,
            timestamp = LocalDateTime.now(),
            sequenceNumber = sequenceNumber,
            rawBytes = data.copyOf()
        )
    }

    /**
     * Parse voltage data from BLE advertisement manufacturer-specific data.
     *
     * Broadcast Mode Format (from manufacturer-specific data payload):
     * The full manufacturer-specific data includes Company ID (2 bytes, little-endian)
     * followed by the payload. Android's ScanRecord.getManufacturerSpecificData()
     * strips the Company ID, so we receive only the payload byte(s).
     *
     * Payload: [VoltageCode]
     *   VoltageCode: 0x01=220V, 0x02=380V, 0x03=22.9KV, 0x04=154KV,
     *                0x05=345KV, 0x06=500KV, 0x07=765KV
     *
     * @param manufacturerData The payload bytes after Company ID (from ScanRecord)
     * @return VoltageReading if valid voltage code found, null otherwise
     */
    fun parseAdvertisementData(manufacturerData: ByteArray): VoltageReading? {
        if (manufacturerData.isEmpty()) {
            return null
        }

        // The manufacturer data payload is just the voltage code byte
        val voltageCode = manufacturerData[0]
        val voltage = VoltageLevel.fromByteCode(voltageCode) ?: return null

        // Only return readings for dangerous voltage levels (not diagnostic codes)
        if (!voltage.isDangerous) {
            return null
        }

        return VoltageReading(
            voltage = voltage,
            timestamp = LocalDateTime.now(),
            sequenceNumber = 0,
            rawBytes = manufacturerData.copyOf()
        )
    }

    /**
     * Create a test packet for debugging/testing.
     *
     * @param voltage The voltage level to encode
     * @param sequenceNumber The sequence number
     * @return Valid 10-byte packet
     */
    fun createTestPacket(voltage: VoltageLevel, sequenceNumber: Int): ByteArray {
        val packet = ByteArray(PACKET_SIZE)

        packet[0] = HEADER_BYTE
        packet[1] = voltage.byteCode
        packet[2] = ((sequenceNumber shr 8) and 0xFF).toByte()  // High byte
        packet[3] = (sequenceNumber and 0xFF).toByte()           // Low byte
        packet[4] = (packet[1].toInt() xor packet[2].toInt() xor packet[3].toInt()).toByte()  // CRC
        // Bytes 5-8 remain 0x00 (padding)
        packet[9] = FOOTER_BYTE

        return packet
    }
}
