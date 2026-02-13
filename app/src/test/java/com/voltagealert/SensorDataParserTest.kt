package com.voltagealert

import com.voltagealert.bluetooth.SensorDataParser
import com.voltagealert.models.VoltageLevel
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SensorDataParser.
 *
 * Tests packet parsing, CRC validation, and error handling.
 */
class SensorDataParserTest {

    @Test
    fun `valid packet should parse correctly`() {
        val voltage = VoltageLevel.VOLTAGE_220V
        val sequenceNumber = 42

        val packet = SensorDataParser.createTestPacket(voltage, sequenceNumber)
        val reading = SensorDataParser.parsePacket(packet)

        assertNotNull("Valid packet should parse successfully", reading)
        assertEquals("Voltage should match", voltage, reading?.voltage)
        assertEquals("Sequence number should match", sequenceNumber, reading?.sequenceNumber)
    }

    @Test
    fun `invalid packet size should return null`() {
        val invalidPacket = ByteArray(5)  // Wrong size
        val reading = SensorDataParser.parsePacket(invalidPacket)

        assertNull("Invalid size packet should return null", reading)
    }

    @Test
    fun `invalid header should return null`() {
        val packet = SensorDataParser.createTestPacket(VoltageLevel.VOLTAGE_220V, 1)
        packet[0] = 0x00.toByte()  // Corrupt header

        val reading = SensorDataParser.parsePacket(packet)
        assertNull("Invalid header should return null", reading)
    }

    @Test
    fun `invalid footer should return null`() {
        val packet = SensorDataParser.createTestPacket(VoltageLevel.VOLTAGE_220V, 1)
        packet[9] = 0x00.toByte()  // Corrupt footer

        val reading = SensorDataParser.parsePacket(packet)
        assertNull("Invalid footer should return null", reading)
    }

    @Test
    fun `invalid CRC should return null`() {
        val packet = SensorDataParser.createTestPacket(VoltageLevel.VOLTAGE_220V, 1)
        packet[4] = (packet[4] + 1).toByte()  // Corrupt CRC

        val reading = SensorDataParser.parsePacket(packet)
        assertNull("Invalid CRC should return null", reading)
    }

    @Test
    fun `unknown voltage code should return null`() {
        val packet = SensorDataParser.createTestPacket(VoltageLevel.VOLTAGE_220V, 1)
        packet[1] = 0xFF.toByte()  // Unknown voltage code

        // Recalculate CRC for corrupted voltage code
        packet[4] = (packet[1].toInt() xor packet[2].toInt() xor packet[3].toInt()).toByte()

        val reading = SensorDataParser.parsePacket(packet)
        assertNull("Unknown voltage code should return null", reading)
    }

    @Test
    fun `all voltage levels should parse correctly`() {
        VoltageLevel.entries.forEach { voltage ->
            val packet = SensorDataParser.createTestPacket(voltage, 100)
            val reading = SensorDataParser.parsePacket(packet)

            assertNotNull("Voltage $voltage should parse", reading)
            assertEquals("Voltage should match", voltage, reading?.voltage)
        }
    }

    @Test
    fun `sequence number should handle full range`() {
        val testCases = listOf(0, 1, 255, 256, 32767, 65535)

        testCases.forEach { seqNum ->
            val packet = SensorDataParser.createTestPacket(VoltageLevel.VOLTAGE_220V, seqNum)
            val reading = SensorDataParser.parsePacket(packet)

            assertNotNull("Sequence $seqNum should parse", reading)
            assertEquals("Sequence number should match", seqNum, reading?.sequenceNumber)
        }
    }

    @Test
    fun `sequence number overflow should wrap correctly`() {
        // Test sequence number wrapping at 16-bit boundary
        val maxSeq = 65535
        val packet = SensorDataParser.createTestPacket(VoltageLevel.VOLTAGE_220V, maxSeq)
        val reading = SensorDataParser.parsePacket(packet)

        assertEquals("Max sequence should parse", maxSeq, reading?.sequenceNumber)
    }

    @Test
    fun `createTestPacket should produce valid packets`() {
        val voltage = VoltageLevel.VOLTAGE_345KV
        val seqNum = 999

        val packet = SensorDataParser.createTestPacket(voltage, seqNum)

        // Verify packet structure
        assertEquals("Packet size should be 10", 10, packet.size)
        assertEquals("Header should be 0xAA", 0xAA.toByte(), packet[0])
        assertEquals("Footer should be 0x55", 0x55.toByte(), packet[9])
        assertEquals("Voltage code should match", voltage.byteCode, packet[1])

        // Verify it parses back correctly
        val reading = SensorDataParser.parsePacket(packet)
        assertNotNull("Test packet should parse", reading)
        assertEquals("Parsed voltage should match", voltage, reading?.voltage)
        assertEquals("Parsed sequence should match", seqNum, reading?.sequenceNumber)
    }

    @Test
    fun `CRC calculation should be consistent`() {
        val packet1 = SensorDataParser.createTestPacket(VoltageLevel.VOLTAGE_220V, 123)
        val packet2 = SensorDataParser.createTestPacket(VoltageLevel.VOLTAGE_220V, 123)

        // Same inputs should produce identical packets
        assertArrayEquals("Identical inputs should produce identical packets", packet1, packet2)
    }

    // === Broadcast Mode: Framed Binary [0xAA][VoltageCode][0x55] Tests ===

    @Test
    fun `parseAdvertisementData should parse framed binary protocol`() {
        // Firmware protocol: [0xAA][VoltageCode][0x55]
        val data = byteArrayOf(0xAA.toByte(), 0x04, 0x55) // 154KV
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNotNull("Framed binary [AA 04 55] should parse", reading)
        assertEquals("Should be 154KV", VoltageLevel.VOLTAGE_154KV, reading?.voltage)
    }

    @Test
    fun `parseAdvertisementData should parse all framed voltage levels`() {
        val expectedVoltages = mapOf(
            0x01.toByte() to VoltageLevel.VOLTAGE_220V,
            0x02.toByte() to VoltageLevel.VOLTAGE_380V,
            0x03.toByte() to VoltageLevel.VOLTAGE_229KV,
            0x04.toByte() to VoltageLevel.VOLTAGE_154KV,
            0x05.toByte() to VoltageLevel.VOLTAGE_345KV,
            0x06.toByte() to VoltageLevel.VOLTAGE_500KV,
            0x07.toByte() to VoltageLevel.VOLTAGE_765KV
        )

        expectedVoltages.forEach { (code, expectedLevel) ->
            val data = byteArrayOf(0xAA.toByte(), code, 0x55)
            val reading = SensorDataParser.parseAdvertisementData(data)

            assertNotNull("Framed code 0x${"%02X".format(code)} should parse", reading)
            assertEquals("Framed code 0x${"%02X".format(code)} should map to $expectedLevel",
                expectedLevel, reading?.voltage)
        }
    }

    // === Broadcast Mode: Raw Binary (single byte, no framing) Tests ===

    @Test
    fun `parseAdvertisementData should parse raw single byte voltage code`() {
        val data = byteArrayOf(0x01) // 220V
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNotNull("Valid voltage code should parse", reading)
        assertEquals("Voltage should be 220V", VoltageLevel.VOLTAGE_220V, reading?.voltage)
    }

    @Test
    fun `parseAdvertisementData should parse all raw voltage levels`() {
        val expectedVoltages = mapOf(
            0x01.toByte() to VoltageLevel.VOLTAGE_220V,
            0x02.toByte() to VoltageLevel.VOLTAGE_380V,
            0x03.toByte() to VoltageLevel.VOLTAGE_229KV,
            0x04.toByte() to VoltageLevel.VOLTAGE_154KV,
            0x05.toByte() to VoltageLevel.VOLTAGE_345KV,
            0x06.toByte() to VoltageLevel.VOLTAGE_500KV,
            0x07.toByte() to VoltageLevel.VOLTAGE_765KV
        )

        expectedVoltages.forEach { (code, expectedLevel) ->
            val data = byteArrayOf(code)
            val reading = SensorDataParser.parseAdvertisementData(data)

            assertNotNull("Voltage code 0x${"%02X".format(code)} should parse", reading)
            assertEquals("Voltage code 0x${"%02X".format(code)} should map to $expectedLevel",
                expectedLevel, reading?.voltage)
        }
    }

    @Test
    fun `parseAdvertisementData should return null for empty data`() {
        val data = byteArrayOf()
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNull("Empty data should return null", reading)
    }

    @Test
    fun `parseAdvertisementData should return null for unknown voltage code`() {
        val data = byteArrayOf(0x99.toByte())
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNull("Unknown voltage code should return null", reading)
    }

    @Test
    fun `parseAdvertisementData should return null for diagnostic codes`() {
        // Diagnostic codes are not dangerous, should not trigger alerts
        val okData = byteArrayOf(0xF0.toByte())
        val ngData = byteArrayOf(0xF1.toByte())

        assertNull("Diagnostic OK should return null", SensorDataParser.parseAdvertisementData(okData))
        assertNull("Diagnostic NG should return null", SensorDataParser.parseAdvertisementData(ngData))
    }

    @Test
    fun `parseAdvertisementData should parse binary with extra bytes`() {
        // If manufacturer data has extra non-ASCII bytes, binary fallback should work
        val data = byteArrayOf(0x05, 0x00, 0xFF.toByte())
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNotNull("Should parse binary voltage code", reading)
        assertEquals("Should be 345KV", VoltageLevel.VOLTAGE_345KV, reading?.voltage)
    }

    // === Broadcast Mode ASCII Advertisement Data Tests ===

    @Test
    fun `parseAdvertisementData should parse ASCII 220V WARNING`() {
        val data = "220V WARNING".toByteArray(Charsets.US_ASCII)
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNotNull("ASCII '220V WARNING' should parse", reading)
        assertEquals("Should be 220V", VoltageLevel.VOLTAGE_220V, reading?.voltage)
    }

    @Test
    fun `parseAdvertisementData should parse ASCII 154KV WARNING`() {
        val data = "154KV WARNING".toByteArray(Charsets.US_ASCII)
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNotNull("ASCII '154KV WARNING' should parse", reading)
        assertEquals("Should be 154KV", VoltageLevel.VOLTAGE_154KV, reading?.voltage)
    }

    @Test
    fun `parseAdvertisementData should parse ASCII 380V WARNING`() {
        val data = "380V WARNING".toByteArray(Charsets.US_ASCII)
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNotNull("ASCII '380V WARNING' should parse", reading)
        assertEquals("Should be 380V", VoltageLevel.VOLTAGE_380V, reading?.voltage)
    }

    @Test
    fun `parseAdvertisementData should parse ASCII 22 point 9KV`() {
        val data = "22.9KV WARNING".toByteArray(Charsets.US_ASCII)
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNotNull("ASCII '22.9KV WARNING' should parse", reading)
        assertEquals("Should be 229KV", VoltageLevel.VOLTAGE_229KV, reading?.voltage)
    }

    @Test
    fun `parseAdvertisementData should parse ASCII 345KV WARNING`() {
        val data = "345KV WARNING".toByteArray(Charsets.US_ASCII)
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNotNull("ASCII '345KV WARNING' should parse", reading)
        assertEquals("Should be 345KV", VoltageLevel.VOLTAGE_345KV, reading?.voltage)
    }

    @Test
    fun `parseAdvertisementData should parse ASCII 500KV WARNING`() {
        val data = "500KV WARNING".toByteArray(Charsets.US_ASCII)
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNotNull("ASCII '500KV WARNING' should parse", reading)
        assertEquals("Should be 500KV", VoltageLevel.VOLTAGE_500KV, reading?.voltage)
    }

    @Test
    fun `parseAdvertisementData should parse ASCII 765KV WARNING`() {
        val data = "765KV WARNING".toByteArray(Charsets.US_ASCII)
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNotNull("ASCII '765KV WARNING' should parse", reading)
        assertEquals("Should be 765KV", VoltageLevel.VOLTAGE_765KV, reading?.voltage)
    }

    @Test
    fun `parseAdvertisementData ASCII should prefer over binary`() {
        // "220V" in ASCII starts with byte 0x32 ('2'), which is binary for 380V
        // ASCII parsing should take priority and correctly return 220V
        val data = "220V WARNING".toByteArray(Charsets.US_ASCII)
        val reading = SensorDataParser.parseAdvertisementData(data)

        assertNotNull("Should parse as ASCII, not binary", reading)
        assertEquals("Should be 220V (ASCII), not 380V (binary 0x32)", VoltageLevel.VOLTAGE_220V, reading?.voltage)
    }
}
