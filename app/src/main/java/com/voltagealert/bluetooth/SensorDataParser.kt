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
     * @param data The raw 10-byte packet
     * @return VoltageReading if valid, null if invalid/corrupted
     */
    fun parsePacket(data: ByteArray): VoltageReading? {
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
