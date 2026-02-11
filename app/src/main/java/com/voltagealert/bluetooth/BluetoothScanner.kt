package com.voltagealert.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import com.voltagealert.models.VoltageReading
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Bluetooth Low Energy scanner for voltage sensor devices.
 *
 * Scans for devices matching:
 * - Service UUID: 0000ffe0-0000-1000-8000-00805f9b34fb (HM-10/JDY-08)
 * - Device name prefix: "VoltSensor-" or "HM-10" or "JDY-08"
 */
class BluetoothScanner(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private val _discoveredDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ScannedDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val deviceMap = mutableMapOf<String, ScannedDevice>()

    // Broadcast mode: emits voltage readings detected from advertisement data
    private val _broadcastReading = MutableSharedFlow<VoltageReading>(extraBufferCapacity = 1)
    val broadcastReading: SharedFlow<VoltageReading> = _broadcastReading.asSharedFlow()

    companion object {
        private const val TAG = "BluetoothScanner"
        // ST9401-UP / ESSYSTEM service UUID (confirmed by manufacturer)
        private val SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

        // Espressif Company ID for manufacturer-specific data (0x02E5)
        private const val ESPRESSIF_COMPANY_ID = 0x02E5

        // Device name prefixes to look for (ST9401-UP confirmed from Mac scan!)
        private val DEVICE_NAME_PREFIXES = listOf("ST9401-UP", "ST940I-UP", "ESSYSTEM", "VoltSensor", "HM-10", "JDY-08", "MLT-BT05")
    }

    data class ScannedDevice(
        val device: BluetoothDevice,
        val name: String?,
        val rssi: Int,
        val serviceUuids: List<UUID>?
    )

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name
            val rssi = result.rssi
            val serviceUuids = result.scanRecord?.serviceUuids?.map {
                UUID.fromString(it.uuid.toString())
            }

            // Broadcast mode: check manufacturer-specific data for voltage code
            val scanRecord = result.scanRecord
            val manufacturerData = scanRecord?.manufacturerSpecificData
            var hasVoltageData = false
            if (manufacturerData != null) {
                // Check Espressif Company ID (0x02E5) for broadcast voltage data
                val espressifData = manufacturerData.get(ESPRESSIF_COMPANY_ID)
                if (espressifData != null) {
                    val reading = SensorDataParser.parseAdvertisementData(espressifData)
                    if (reading != null) {
                        Log.d(TAG, "⚡ BROADCAST VOLTAGE: ${reading.voltage} from ${device.address} RSSI: $rssi")
                        _broadcastReading.tryEmit(reading)
                        hasVoltageData = true
                    }
                }

                // Also check other manufacturer IDs for legacy detection
                for (i in 0 until manufacturerData.size()) {
                    val manufacturerId = manufacturerData.keyAt(i)
                    if (manufacturerId == ESPRESSIF_COMPANY_ID) continue // Already handled above
                    val data = manufacturerData.valueAt(i)
                    val dataString = String(data, Charsets.UTF_8)
                    if (dataString.contains("220V") || dataString.contains("WARNING") || dataString.contains("ST940")) {
                        Log.d(TAG, "⚡ FOUND VOLTAGE DEVICE! ${device.address} - MfgID: $manufacturerId, Data: ${data.contentToString()}, String: $dataString")
                        hasVoltageData = true
                    }
                }
            }

            if (hasVoltageData) {
                Log.d(TAG, "🎯 VOLTAGE SENSOR DETECTED: $name (${device.address}) RSSI: $rssi")
            } else {
                Log.d(TAG, "Device found: $name (${device.address}) RSSI: $rssi")
            }

            // Add to discovered devices
            val scannedDevice = ScannedDevice(device, name, rssi, serviceUuids)
            deviceMap[device.address] = scannedDevice
            _discoveredDevices.value = deviceMap.values.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _isScanning.value = false
            _scanError.value = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already in progress"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE not supported"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                else -> "Unknown error: $errorCode"
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { result ->
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }
    }

    /**
     * Start scanning for BLE voltage sensor devices.
     * ST9401-UP uses ESP32-S3 with NimBLE (BLE only, not Classic Bluetooth).
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null) {
            _scanError.value = "Bluetooth not available"
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            _scanError.value = "Bluetooth is disabled"
            return
        }

        if (_isScanning.value) {
            Log.w(TAG, "Scan already in progress")
            return
        }

        deviceMap.clear()
        _discoveredDevices.value = emptyList()
        _scanError.value = null
        _isScanning.value = true

        try {
            Log.d(TAG, "Starting BLE scan for voltage sensors...")

            // Build BLE scan settings for optimal discovery
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // Filter for ESSYSTEM device only (confirmed device name)
            val scanFilters = listOf(
                ScanFilter.Builder()
                    .setDeviceName("ESSYSTEM")  // Only scan for our voltage sensor
                    .build()
            )

            // Start BLE scan
            bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
            Log.d(TAG, "BLE scan started WITH filter: device name = ESSYSTEM")

        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permissions", e)
            _scanError.value = "Missing Bluetooth permissions"
            _isScanning.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan", e)
            _scanError.value = "Failed to start scan: ${e.message}"
            _isScanning.value = false
        }
    }

    /**
     * Stop BLE scanning.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) {
            return
        }

        try {
            // Stop BLE scan
            bleScanner?.stopScan(scanCallback)
            _isScanning.value = false
            Log.d(TAG, "BLE scan stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permissions", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop BLE scan", e)
        }
    }

    /**
     * Find best matching device (strongest RSSI with matching criteria).
     */
    fun findBestDevice(): ScannedDevice? {
        return _discoveredDevices.value
            .filter { device ->
                // Check if device name matches our prefixes
                val nameMatches = device.name?.let { name ->
                    DEVICE_NAME_PREFIXES.any { prefix ->
                        name.startsWith(prefix, ignoreCase = true)
                    }
                } ?: false

                // Check if device advertises our service UUID
                val uuidMatches = device.serviceUuids?.contains(SERVICE_UUID) ?: false

                nameMatches || uuidMatches
            }
            .maxByOrNull { it.rssi }
    }

    /**
     * Clear discovered devices list.
     */
    fun clearDevices() {
        deviceMap.clear()
        _discoveredDevices.value = emptyList()
    }
}
