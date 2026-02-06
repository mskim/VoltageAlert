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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private var isClassicScanActive = false

    // BroadcastReceiver for Classic Bluetooth discovery
    private val classicDiscoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                    device?.let {
                        val name = it.name
                        Log.d(TAG, "Classic BT Device found: $name (${it.address}) RSSI: $rssi")

                        // Add to discovered devices
                        val scannedDevice = ScannedDevice(it, name, rssi, null)
                        deviceMap[it.address] = scannedDevice
                        _discoveredDevices.value = deviceMap.values.sortedByDescending { it.rssi }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Classic BT discovery finished")
                    isClassicScanActive = false
                }
            }
        }
    }

    companion object {
        private const val TAG = "BluetoothScanner"
        private val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")

        // Device name prefixes to look for
        private val DEVICE_NAME_PREFIXES = listOf("ST940I-UP", "VoltSensor", "HM-10", "JDY-08", "MLT-BT05")
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

            Log.d(TAG, "Device found: $name (${device.address}) RSSI: $rssi")

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
     * Start scanning for voltage sensor devices.
     * Filters by service UUID and device name.
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

        if (_isScanning.value || isClassicScanActive) {
            Log.w(TAG, "Scan already in progress")
            return
        }

        deviceMap.clear()
        _discoveredDevices.value = emptyList()
        _scanError.value = null
        _isScanning.value = true

        try {
            // First, check already bonded/paired devices
            val bondedDevices = bluetoothAdapter.bondedDevices
            Log.d(TAG, "Checking ${bondedDevices.size} bonded devices")

            bondedDevices.forEach { device ->
                val name = device.name
                Log.d(TAG, "Bonded device: $name (${device.address})")

                // Check if it's our voltage sensor
                if (name != null && DEVICE_NAME_PREFIXES.any { name.contains(it, ignoreCase = true) }) {
                    Log.d(TAG, "Found paired voltage sensor: $name")
                    val scannedDevice = ScannedDevice(device, name, -50, null) // Use default RSSI
                    deviceMap[device.address] = scannedDevice
                }
            }

            // If we found paired devices, use them immediately
            if (deviceMap.isNotEmpty()) {
                _discoveredDevices.value = deviceMap.values.sortedByDescending { it.rssi }
                _isScanning.value = false
                Log.d(TAG, "Using ${deviceMap.size} paired device(s)")
                return
            }

            // If no paired devices found, start discovery
            Log.d(TAG, "No paired voltage sensors found, starting discovery")

            // Register receiver for Classic Bluetooth discovery
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(classicDiscoveryReceiver, filter)

            // Start Classic Bluetooth discovery (for ST940I-UP)
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }

            val discoveryStarted = bluetoothAdapter.startDiscovery()
            if (discoveryStarted) {
                isClassicScanActive = true
                Log.d(TAG, "Started Classic Bluetooth discovery")
            } else {
                Log.e(TAG, "Failed to start Classic Bluetooth discovery")
                _scanError.value = "Failed to start Bluetooth discovery"
                _isScanning.value = false
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permissions", e)
            _scanError.value = "Missing Bluetooth permissions"
            _isScanning.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
            _scanError.value = "Failed to start scan: ${e.message}"
            _isScanning.value = false
        }
    }

    /**
     * Stop scanning.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value && !isClassicScanActive) {
            return
        }

        try {
            // Stop Classic Bluetooth discovery
            if (isClassicScanActive) {
                bluetoothAdapter.cancelDiscovery()
                isClassicScanActive = false
            }

            // Unregister receiver
            try {
                context.unregisterReceiver(classicDiscoveryReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver not registered, ignore
            }

            _isScanning.value = false
            Log.d(TAG, "Scan stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permissions", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop scan", e)
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
