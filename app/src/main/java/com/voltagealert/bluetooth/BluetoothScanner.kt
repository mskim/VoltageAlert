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
    private val _broadcastReading = MutableSharedFlow<VoltageReading>(extraBufferCapacity = 64)
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

            // Broadcast mode: extract voltage from advertisement data
            // Try multiple sources: manufacturer data, service data, raw bytes
            val scanRecord = result.scanRecord
            var hasVoltageData = false

            // Log full scan record for debugging (only for our target device)
            val isTargetDevice = name != null && DEVICE_NAME_PREFIXES.any { name.startsWith(it, ignoreCase = true) }
            if (isTargetDevice && scanRecord != null) {
                val rawBytes = scanRecord.bytes
                val rawHex = rawBytes?.joinToString(" ") { "%02X".format(it) } ?: "null"
                Log.d(TAG, "📦 SCAN RECORD from $name (${device.address}): $rawHex")

                val manufacturerData = scanRecord.manufacturerSpecificData
                if (manufacturerData != null) {
                    for (i in 0 until manufacturerData.size()) {
                        val mfgId = manufacturerData.keyAt(i)
                        val mfgData = manufacturerData.valueAt(i)
                        val hex = mfgData.joinToString(" ") { "%02X".format(it) }
                        val ascii = String(mfgData, Charsets.US_ASCII).replace(Regex("[^\\x20-\\x7E]"), ".")
                        Log.d(TAG, "  MFG[0x${"%04X".format(mfgId)}]: hex=[$hex] ascii=[$ascii]")
                    }
                }

                val serviceData = scanRecord.getServiceData(ParcelUuid(SERVICE_UUID))
                if (serviceData != null) {
                    val hex = serviceData.joinToString(" ") { "%02X".format(it) }
                    val ascii = String(serviceData, Charsets.US_ASCII).replace(Regex("[^\\x20-\\x7E]"), ".")
                    Log.d(TAG, "  SVC[$SERVICE_UUID]: hex=[$hex] ascii=[$ascii]")
                }
            }

            // Source 1: Manufacturer-specific data (all company IDs)
            val manufacturerData = scanRecord?.manufacturerSpecificData
            if (manufacturerData != null && !hasVoltageData) {
                for (i in 0 until manufacturerData.size()) {
                    val manufacturerId = manufacturerData.keyAt(i)
                    val data = manufacturerData.valueAt(i)
                    val reading = SensorDataParser.parseAdvertisementData(data)
                    if (reading != null) {
                        Log.d(TAG, "⚡ VOLTAGE from MFG[0x${"%04X".format(manufacturerId)}]: ${reading.voltage}")
                        _broadcastReading.tryEmit(reading)
                        hasVoltageData = true
                        break
                    }
                }
            }

            // Source 2: Service data for our service UUID (FFF0)
            if (!hasVoltageData && scanRecord != null) {
                val serviceData = scanRecord.getServiceData(ParcelUuid(SERVICE_UUID))
                if (serviceData != null) {
                    val reading = SensorDataParser.parseAdvertisementData(serviceData)
                    if (reading != null) {
                        Log.d(TAG, "⚡ VOLTAGE from SVC[$SERVICE_UUID]: ${reading.voltage}")
                        _broadcastReading.tryEmit(reading)
                        hasVoltageData = true
                    }
                }
            }

            // Source 3: Raw scan record bytes (last resort - search for voltage text)
            if (!hasVoltageData && scanRecord != null) {
                val rawBytes = scanRecord.bytes
                if (rawBytes != null) {
                    val reading = SensorDataParser.parseAdvertisementData(rawBytes)
                    if (reading != null) {
                        Log.d(TAG, "⚡ VOLTAGE from RAW BYTES: ${reading.voltage}")
                        _broadcastReading.tryEmit(reading)
                        hasVoltageData = true
                    }
                }
            }

            if (hasVoltageData) {
                Log.d(TAG, "🎯 VOLTAGE DETECTED: $name (${device.address}) RSSI: $rssi")
            } else if (isTargetDevice) {
                Log.d(TAG, "📡 Target device found but no voltage data: $name (${device.address}) RSSI: $rssi")
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

            // Aggressive scan settings: report every advertisement, no deduplication.
            // Without MATCH_MODE_AGGRESSIVE, Samsung devices reduce callback frequency
            // for known devices after ~6 scan results (BLE cache optimization).
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()

            // Multiple OR'd filters for maximum compatibility across OEMs.
            // Some devices (Lenovo/Android 15) may not support device name filter.
            val scanFilters = listOf(
                // Filter 1: by device name
                ScanFilter.Builder()
                    .setDeviceName("ESSYSTEM")
                    .build(),
                // Filter 2: by service UUID (fallback if name filter doesn't work)
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(SERVICE_UUID))
                    .build()
            )

            // Start BLE scan
            bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
            Log.d(TAG, "BLE scan started WITH filters: name=ESSYSTEM OR uuid=$SERVICE_UUID")

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
     * Flush BLE scan cache using flushPendingScanResults().
     * Used for periodic cache maintenance during long continuous detections.
     */
    @SuppressLint("MissingPermission")
    fun flushScanCache() {
        if (!_isScanning.value) return

        try {
            bleScanner?.flushPendingScanResults(scanCallback)
            Log.d(TAG, "🔄 Flushed BLE scan cache")
        } catch (e: Exception) {
            Log.d(TAG, "flushPendingScanResults not supported: ${e.message}")
        }
    }

    /**
     * Full scan stop+restart to completely clear Samsung BLE scan deduplication cache.
     * More reliable than flushPendingScanResults() which may not reset the
     * callback frequency throttling on Samsung devices.
     * Must be called infrequently (max 3 times per 30s to stay under Android's 5-per-30s limit).
     */
    @SuppressLint("MissingPermission")
    fun restartScan() {
        if (!_isScanning.value) return

        try {
            Log.d(TAG, "🔄 Restarting scan to clear BLE cache...")
            bleScanner?.stopScan(scanCallback)

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            val scanFilters = listOf(
                ScanFilter.Builder()
                    .setDeviceName("ESSYSTEM")
                    .build(),
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(SERVICE_UUID))
                    .build()
            )

            bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
            Log.d(TAG, "🔄 Scan restarted successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permissions for scan restart", e)
        } catch (e: Exception) {
            Log.w(TAG, "Scan restart failed: ${e.message}")
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
