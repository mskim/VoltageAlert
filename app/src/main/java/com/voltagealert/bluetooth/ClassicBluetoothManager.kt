package com.voltagealert.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.voltagealert.models.ConnectionStatus
import com.voltagealert.models.VoltageReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Manages Classic Bluetooth connection to voltage sensor using SPP (Serial Port Profile).
 *
 * Connects to paired Classic Bluetooth devices like ST940I-UP and reads voltage data
 * from the serial stream.
 */
class ClassicBluetoothManager(
    private val onReadingReceived: (VoltageReading) -> Unit,
    private val onConnectionStatusChanged: (ConnectionStatus) -> Unit,
    private val onError: () -> Unit
) {
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "ClassicBTManager"

        // Standard SPP UUID for serial communication
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

        // Packet parsing constants
        private const val HEADER_BYTE: Byte = 0xAA.toByte()
        private const val FOOTER_BYTE: Byte = 0x55.toByte()
        private const val PACKET_SIZE = 10
        private const val MAX_BUFFER_SIZE = 1024
    }

    /**
     * Connect to a Classic Bluetooth device.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        scope.launch {
            try {
                Log.d(TAG, "Connecting to ${device.name} (${device.address})")
                onConnectionStatusChanged(ConnectionStatus.CONNECTING)

                // Create RFCOMM socket using SPP UUID
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)

                // Connect to the device
                bluetoothSocket?.connect()

                if (bluetoothSocket?.isConnected == true) {
                    inputStream = bluetoothSocket?.inputStream
                    outputStream = bluetoothSocket?.outputStream

                    Log.d(TAG, "Connected successfully to ${device.name}")
                    onConnectionStatusChanged(ConnectionStatus.CONNECTED)

                    // Start reading data from the serial stream
                    startReading()
                } else {
                    throw IOException("Failed to connect socket")
                }

            } catch (e: IOException) {
                Log.e(TAG, "Connection failed: ${e.message}", e)
                onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)
                onError()
                disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during connection: ${e.message}", e)
                onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)
                onError()
                disconnect()
            }
        }
    }

    /**
     * Start reading data from the serial input stream.
     */
    private fun startReading() {
        readJob = scope.launch {
            val buffer = ByteArray(MAX_BUFFER_SIZE)
            val packetBuffer = mutableListOf<Byte>()

            try {
                while (isActive && bluetoothSocket?.isConnected == true) {
                    inputStream?.let { stream ->
                        val available = stream.available()
                        if (available > 0) {
                            val bytesRead = stream.read(buffer, 0, minOf(available, buffer.size))

                            if (bytesRead > 0) {
                                Log.d(TAG, "Read $bytesRead bytes from serial stream")

                                // Add bytes to packet buffer
                                for (i in 0 until bytesRead) {
                                    packetBuffer.add(buffer[i])

                                    // Try to extract complete packets
                                    if (packetBuffer.size >= PACKET_SIZE) {
                                        tryExtractPacket(packetBuffer)
                                    }
                                }

                                // Prevent buffer overflow
                                if (packetBuffer.size > MAX_BUFFER_SIZE) {
                                    Log.w(TAG, "Buffer overflow, clearing old data")
                                    packetBuffer.clear()
                                }
                            }
                        } else {
                            // No data available, sleep briefly
                            Thread.sleep(50)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading from stream: ${e.message}", e)
                onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)
                onError()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during read: ${e.message}", e)
                onError()
            }
        }
    }

    /**
     * Try to extract a complete packet from the buffer.
     */
    private fun tryExtractPacket(buffer: MutableList<Byte>) {
        // Find header byte
        val headerIndex = buffer.indexOf(HEADER_BYTE)

        if (headerIndex == -1) {
            // No header found, discard all data before next potential header
            buffer.clear()
            return
        }

        // Remove any junk before header
        if (headerIndex > 0) {
            repeat(headerIndex) { buffer.removeAt(0) }
        }

        // Check if we have a complete packet
        if (buffer.size >= PACKET_SIZE) {
            // Extract potential packet
            val packet = ByteArray(PACKET_SIZE)
            for (i in 0 until PACKET_SIZE) {
                packet[i] = buffer[i]
            }

            // Validate and parse packet
            val reading = SensorDataParser.parsePacket(packet)

            if (reading != null) {
                Log.d(TAG, "Valid packet received: ${reading.voltage}")
                onReadingReceived(reading)

                // Remove parsed packet from buffer
                repeat(PACKET_SIZE) { buffer.removeAt(0) }
            } else {
                // Invalid packet, remove header and try again
                Log.w(TAG, "Invalid packet, removing header byte")
                buffer.removeAt(0)
            }
        }
    }

    /**
     * Disconnect from the device.
     */
    fun disconnect() {
        scope.launch {
            try {
                Log.d(TAG, "Disconnecting")

                readJob?.cancel()
                readJob = null

                inputStream?.close()
                outputStream?.close()
                bluetoothSocket?.close()

                inputStream = null
                outputStream = null
                bluetoothSocket = null

                onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)
                Log.d(TAG, "Disconnected")

            } catch (e: IOException) {
                Log.e(TAG, "Error during disconnect: ${e.message}", e)
            }
        }
    }

    /**
     * Check if currently connected.
     */
    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        disconnect()
        scope.cancel()
    }
}
