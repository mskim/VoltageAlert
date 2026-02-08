package com.voltagealert.bluetooth

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voltagealert.R
import com.voltagealert.models.ConnectionStatus
import com.voltagealert.models.VoltageReading
import com.voltagealert.testing.MockBluetoothDevice
import com.voltagealert.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.suspend
import java.util.UUID

/**
 * Foreground service for continuous Bluetooth connection to voltage sensor.
 *
 * Uses Nordic BLE library for robust connection management and auto-reconnect.
 */
class BluetoothService : Service() {
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var bleManager: SensorBleManager? = null
    private var mockJob: Job? = null
    private var useMockMode = false
    private var scanner: BluetoothScanner? = null
    private var scanJob: Job? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _latestReading = MutableStateFlow<VoltageReading?>(null)
    val latestReading: StateFlow<VoltageReading?> = _latestReading.asStateFlow()

    private val _errorCount = MutableStateFlow(0)
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothScanner.ScannedDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothScanner.ScannedDevice>> = _discoveredDevices.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    companion object {
        private const val TAG = "BluetoothService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "VoltageAlertService"

        // ST9401-UP / ESSYSTEM UUIDs (confirmed by manufacturer)
        private val SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val CHARACTERISTIC_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        scanner = BluetoothScanner(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        val notification = createNotification("Initializing...")
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    /**
     * Connect to BLE voltage sensor device.
     * ST9401-UP uses ESP32-S3 with NimBLE - BLE with "Just Works" pairing (no PIN).
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        serviceScope.launch {
            try {
                _connectionStatus.value = ConnectionStatus.CONNECTING
                Log.d(TAG, "Connecting to BLE device: ${device.name} (${device.address})")

                // Create BLE manager
                bleManager = SensorBleManager(
                    context = this@BluetoothService,
                    onReadingReceived = { reading ->
                        Log.d(TAG, "🔄 Emitting reading to UI: ${reading.voltage}")
                        _latestReading.value = reading
                        _errorCount.value = 0
                    },
                    onConnectionStatusChanged = { status ->
                        _connectionStatus.value = status
                        updateNotification(status)
                    },
                    onError = {
                        _errorCount.value = _errorCount.value + 1
                    }
                )

                // Connect via BLE GATT
                // NimBLE "Just Works" pairing - no PIN required, automatic pairing
                bleManager?.connect(device)
                    ?.useAutoConnect(true)
                    ?.retry(3, 100)
                    ?.timeout(10000)
                    ?.suspend()

                Log.d(TAG, "✓ Connected to BLE device: ${device.address}")

            } catch (e: Exception) {
                Log.e(TAG, "BLE connection failed", e)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _errorCount.value = _errorCount.value + 1

                // Attempt reconnection after delay
                delay(5000)
                if (_connectionStatus.value == ConnectionStatus.DISCONNECTED) {
                    Log.d(TAG, "Retrying connection...")
                    connect(device)  // Retry
                }
            }
        }
    }

    /**
     * Disconnect from the current BLE device.
     */
    fun disconnect() {
        serviceScope.launch {
            bleManager?.disconnect()?.suspend()
            bleManager = null
            stopMockMode()
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _latestReading.value = null
            _errorCount.value = 0
        }
    }

    /**
     * Start mock Bluetooth mode for testing without physical sensor.
     */
    fun startMockMode(scenario: MockBluetoothDevice.Scenario = MockBluetoothDevice.Scenario.MIXED) {
        Log.d(TAG, "Starting mock mode with scenario: $scenario")
        useMockMode = true

        // Disconnect any real Bluetooth connection
        serviceScope.launch {
            bleManager?.disconnect()?.suspend()
            bleManager = null
        }

        // Start emitting mock data
        mockJob = serviceScope.launch {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            delay(1000) // Simulate connection delay
            _connectionStatus.value = ConnectionStatus.CONNECTED
            updateNotification(ConnectionStatus.CONNECTED)

            MockBluetoothDevice.generateReadings(scenario, intervalMs = 3000).collect { reading ->
                Log.d(TAG, "Mock reading: ${reading.voltage.displayNameRes}")
                _latestReading.value = reading
                _errorCount.value = 0
            }
        }
    }

    /**
     * Stop mock Bluetooth mode.
     */
    fun stopMockMode() {
        Log.d(TAG, "Stopping mock mode")
        useMockMode = false
        mockJob?.cancel()
        mockJob = null
    }

    /**
     * Check if currently in mock mode.
     */
    fun isMockMode(): Boolean = useMockMode

    /**
     * Connect directly to a device by MAC address (bypass scanning).
     */
    @SuppressLint("MissingPermission")
    fun connectByMacAddress(macAddress: String) {
        serviceScope.launch {
            try {
                Log.d(TAG, "🎯 Attempting direct connection to MAC: $macAddress")
                val bluetoothManager = this@BluetoothService.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                val bluetoothAdapter = bluetoothManager.adapter

                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                    Log.e(TAG, "Bluetooth not available or disabled")
                    return@launch
                }

                // Get device by MAC address
                val device = bluetoothAdapter.getRemoteDevice(macAddress)
                Log.d(TAG, "📱 Got BluetoothDevice for $macAddress")

                // Connect directly
                connect(device)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to MAC address $macAddress: ${e.message}", e)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    /**
     * Clear the status message (used when resuming the app to remove stale messages).
     */
    fun clearStatusMessage() {
        _statusMessage.value = ""
    }

    /**
     * Start scanning for voltage sensor devices.
     * Automatically connects to the best matching device when found.
     */
    fun startScanning(autoConnect: Boolean = true) {
        Log.d(TAG, "Starting Bluetooth scan (autoConnect=$autoConnect)")

        // Stop mock mode if active
        if (useMockMode) {
            stopMockMode()
        }

        _connectionStatus.value = ConnectionStatus.SCANNING
        _statusMessage.value = "Scanning for devices..."
        _discoveredDevices.value = emptyList()  // Clear old scan results
        updateNotification(ConnectionStatus.SCANNING)

        scanner?.startScan()

        // Observe scan results
        scanJob = serviceScope.launch {
            scanner?.discoveredDevices?.collect { devices ->
                _discoveredDevices.value = devices
                _statusMessage.value = "Found ${devices.size} devices..."
                Log.d(TAG, "Discovered ${devices.size} devices")
            }
        }

        // Auto-connect after collecting devices
        if (autoConnect) {
            serviceScope.launch {
                delay(5000) // Wait 5 seconds to find ESSYSTEM (BLE devices found quickly with name filter)

                val devices = _discoveredDevices.value
                if (devices.isEmpty()) {
                    // No devices found - rescan after delay
                    stopScanning()
                    Log.w(TAG, "⚠️ No devices found in scan. Rescanning in 3 seconds...")
                    _statusMessage.value = "No devices found. Rescanning..."
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    delay(3000) // Wait 3 seconds before rescanning
                    if (_connectionStatus.value == ConnectionStatus.DISCONNECTED) {
                        Log.d(TAG, "🔄 Auto-rescanning (no devices found)...")
                        startScanning(autoConnect = true)
                    }
                    return@launch
                }

                if (_connectionStatus.value != ConnectionStatus.SCANNING) {
                    return@launch
                }

                stopScanning()

                Log.d(TAG, "📊 Auto-connect: Found ${devices.size} total devices after scan")
                _statusMessage.value = "Found ${devices.size} devices! Testing for voltage sensor..."

                // LOG ALL DISCOVERED DEVICE NAMES for debugging
                Log.d(TAG, "📱 ALL DISCOVERED DEVICES:")
                devices.forEach { scannedDevice ->
                    val name = scannedDevice.name ?: "[NO NAME]"
                    val mac = scannedDevice.device.address
                    val rssi = scannedDevice.rssi
                    Log.d(TAG, "  - $name ($mac) RSSI: $rssi")
                }

                    // FIRST: Try the last successfully connected device (if saved)
                    val lastMac = getLastConnectedDevice()
                    val targetDevice = if (lastMac != null) {
                        devices.find { it.device.address.equals(lastMac, ignoreCase = true) }
                    } else {
                        null
                    }

                    if (targetDevice != null) {
                        Log.d(TAG, "🎯 Found last connected device: ${targetDevice.device.address}")
                        Log.d(TAG, "🔌 Connecting to saved device (${targetDevice.device.address}) RSSI: ${targetDevice.rssi}")
                        _statusMessage.value = "Connecting to saved device..."

                        try {
                            _connectionStatus.value = ConnectionStatus.CONNECTING
                            connect(targetDevice.device)
                            return@launch // Exit if successful
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to connect to ESSYSTEM: ${e.message}")
                        }
                    } else if (lastMac != null) {
                        Log.w(TAG, "⚠️ Last connected device $lastMac not found in scan")
                    }

                    // ONLY try ESSYSTEM devices (our confirmed device name)
                    val essystemDevices = devices.filter { device ->
                        device.name?.contains("ESSYSTEM", ignoreCase = true) == true
                    }.sortedByDescending { it.rssi }

                    if (essystemDevices.isEmpty()) {
                        Log.e(TAG, "❌ No ESSYSTEM devices found among ${devices.size} devices")
                        _statusMessage.value = "No ESSYSTEM found. Rescanning..."
                        _connectionStatus.value = ConnectionStatus.DISCONNECTED
                        // Auto-rescan after delay
                        delay(3000) // Wait 3 seconds before rescanning
                        if (_connectionStatus.value == ConnectionStatus.DISCONNECTED) {
                            Log.d(TAG, "🔄 Auto-rescanning (ESSYSTEM not found)...")
                            startScanning(autoConnect = true)
                        }
                        return@launch
                    }

                    val sortedDevices = essystemDevices

                    Log.d(TAG, "📊 Found ${devices.size} total devices: ${essystemDevices.size} ESSYSTEM")

                    Log.d(TAG, "🔍 Trying to connect to ${sortedDevices.size} ESSYSTEM device(s)...")

                    // Try each device until we find one with the voltage service
                    for ((index, scannedDevice) in sortedDevices.withIndex()) {
                        if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
                            Log.d(TAG, "✅ Already connected, stopping search")
                            break
                        }

                        Log.d(TAG, "🔌 Attempt ${index + 1}/${sortedDevices.size}: Trying ${scannedDevice.name ?: "unnamed"} (${scannedDevice.device.address}) RSSI: ${scannedDevice.rssi}")
                        _statusMessage.value = "Testing device ${index + 1}/${sortedDevices.size}: ${scannedDevice.device.address.takeLast(8)}"

                        try {
                            _connectionStatus.value = ConnectionStatus.CONNECTING

                            // Try to connect with shorter timeout for faster iteration
                            bleManager = SensorBleManager(
                                context = this@BluetoothService,
                                onReadingReceived = { reading ->
                                    Log.d(TAG, "🔄 Emitting reading to UI: ${reading.voltage}")
                                    _latestReading.value = reading
                                    _errorCount.value = 0
                                },
                                onConnectionStatusChanged = { status ->
                                    _connectionStatus.value = status
                                    updateNotification(status)
                                },
                                onError = {
                                    _errorCount.value = _errorCount.value + 1
                                }
                            )

                            bleManager?.connect(scannedDevice.device)
                                ?.useAutoConnect(false) // Don't auto-reconnect during discovery
                                ?.retry(1, 100)
                                ?.timeout(5000) // 5 second timeout
                                ?.suspend()

                            Log.d(TAG, "✅ SUCCESS! Found voltage sensor: ${scannedDevice.device.address}")

                            // Save MAC address for future quick connections
                            saveLastConnectedDevice(scannedDevice.device.address)

                            _statusMessage.value = "✅ Connected to voltage sensor!"
                            return@launch // Found it, stop trying

                        } catch (e: Exception) {
                            Log.w(TAG, "❌ Device ${scannedDevice.device.address} failed (likely not voltage sensor): ${e.message}")
                            _statusMessage.value = "Device ${index + 1} not sensor, trying next..."
                            Log.d(TAG, "Cleaning up connection...")
                            try {
                                withTimeout(2000) {
                                    bleManager?.disconnect()?.suspend()
                                }
                            } catch (disconnectError: Exception) {
                                Log.d(TAG, "Disconnect failed or timed out: ${disconnectError.message}")
                            } finally {
                                try {
                                    bleManager?.close()
                                } catch (closeError: Exception) {
                                    Log.d(TAG, "Close failed: ${closeError.message}")
                                }
                            }
                            bleManager = null
                            _connectionStatus.value = ConnectionStatus.DISCONNECTED
                            Log.d(TAG, "✅ Ready for next attempt...")
                            delay(500) // Small delay before trying next device
                        }
                    }

                // If we tried all devices and none worked
                if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
                    Log.e(TAG, "❌ No voltage sensor found among ${sortedDevices.size} devices")
                    _statusMessage.value = "No voltage sensor found. Tried ${sortedDevices.size} devices."
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    updateNotification(ConnectionStatus.DISCONNECTED)
                }
            }
        }

        // Auto-stop scan after 30 seconds if no device found (allow time for intermittent advertising)
        serviceScope.launch {
            delay(30000)
            if (_connectionStatus.value == ConnectionStatus.SCANNING) {
                Log.d(TAG, "Scan timeout - no devices found")
                stopScanning()

                // Try direct connection to known MAC address as last resort
                Log.d(TAG, "🔧 Attempting direct connection to known ESSYSTEM MAC address...")
                connectByMacAddress("30:ED:AD:D4:84:4E")
            }
        }
    }

    /**
     * Stop scanning.
     */
    fun stopScanning() {
        Log.d(TAG, "Stopping scan")
        scanner?.stopScan()
        scanJob?.cancel()
        scanJob = null
    }

    /**
     * Connect to a specific device from scan results.
     */
    fun connectToDevice(deviceAddress: String) {
        val device = _discoveredDevices.value.find { it.device.address == deviceAddress }?.device
        if (device != null) {
            stopScanning()
            connect(device)
        } else {
            Log.e(TAG, "Device not found: $deviceAddress")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            bleManager?.disconnect()?.suspend()
        }
        Log.d(TAG, "Service destroyed")
    }

    /**
     * Save the MAC address of successfully connected device.
     */
    private fun saveLastConnectedDevice(macAddress: String) {
        getSharedPreferences("VoltageAlert", MODE_PRIVATE)
            .edit()
            .putString("last_connected_mac", macAddress)
            .apply()
        Log.d(TAG, "💾 Saved last connected device: $macAddress")
    }

    /**
     * Get the MAC address of the last successfully connected device.
     */
    private fun getLastConnectedDevice(): String? {
        return getSharedPreferences("VoltageAlert", MODE_PRIVATE)
            .getString("last_connected_mac", null)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.service_channel_description)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: ConnectionStatus) {
        val text = when (status) {
            ConnectionStatus.CONNECTED -> getString(R.string.status_connected)
            ConnectionStatus.CONNECTING -> getString(R.string.status_connecting)
            ConnectionStatus.SCANNING -> getString(R.string.status_scanning)
            ConnectionStatus.DISCONNECTED -> getString(R.string.status_disconnected)
        }

        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * BLE Manager implementation using Nordic library.
     */
    private inner class SensorBleManager(
        context: android.content.Context,
        private val onReadingReceived: (VoltageReading) -> Unit,
        private val onConnectionStatusChanged: (ConnectionStatus) -> Unit,
        private val onError: () -> Unit
    ) : BleManager(context) {

        private var dataCharacteristic: BluetoothGattCharacteristic? = null

        override fun getMinLogPriority(): Int = Log.VERBOSE

        override fun log(priority: Int, message: String) {
            Log.println(priority, TAG, message)
        }

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            // Log ALL services and characteristics for debugging
            Log.d(TAG, "🔍 DISCOVERING ALL SERVICES AND CHARACTERISTICS:")
            gatt.services.forEach { service ->
                Log.d(TAG, "  📦 Service: ${service.uuid}")
                service.characteristics.forEach { char ->
                    val props = mutableListOf<String>()
                    if ((char.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) props.add("READ")
                    if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) props.add("WRITE")
                    if ((char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) props.add("NOTIFY")
                    if ((char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) props.add("INDICATE")
                    Log.d(TAG, "    📝 Characteristic: ${char.uuid} - Properties: ${props.joinToString(", ")}")
                }
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Required service $SERVICE_UUID not found")
                return false
            }

            dataCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
            if (dataCharacteristic == null) {
                Log.e(TAG, "Required characteristic $CHARACTERISTIC_UUID not found")
                return false
            }

            val properties = dataCharacteristic?.properties ?: 0
            val canNotify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0

            if (!canNotify) {
                Log.e(TAG, "Characteristic does not support notifications")
                return false
            }

            return true
        }

        override fun initialize() {
            requestMtu(512).enqueue()

            dataCharacteristic?.let { char ->
                Log.d(TAG, "🔔 Setting up notification callback for characteristic: ${char.uuid}")

                setNotificationCallback(char).with { _, data ->
                    val bytes = data.value
                    Log.d(TAG, "🔔 NOTIFICATION CALLBACK TRIGGERED! Data null? ${bytes == null}")
                    if (bytes != null) {
                        // Log raw bytes received
                        val hexString = bytes.joinToString(" ") { "%02X".format(it) }
                        Log.d(TAG, "📡 RAW DATA RECEIVED via NOTIFY (${bytes.size} bytes): $hexString")

                        val reading = SensorDataParser.parsePacket(bytes)
                        if (reading != null) {
                            Log.d(TAG, "✅ Parsed voltage: ${reading.voltage}")
                            onReadingReceived(reading)
                        } else {
                            Log.w(TAG, "❌ Invalid packet received - parsing failed")
                            onError()
                        }
                    } else {
                        Log.w(TAG, "⚠️ Notification received but data is null")
                    }
                }

                Log.d(TAG, "🔔 Enabling notifications for characteristic: ${char.uuid}")
                enableNotifications(char).done {
                    Log.d(TAG, "✅ Notifications ENABLED successfully on ${char.uuid}")
                    // Start polling via READ as well (manufacturer hint shows READ + NOTIFY)
                    startPollingCharacteristic(char)
                }.fail { device, status ->
                    Log.e(TAG, "❌ Failed to enable notifications: status=$status")
                }.enqueue()
            }

            onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        }

        private fun startPollingCharacteristic(char: BluetoothGattCharacteristic) {
            Log.d(TAG, "📖 Starting READ polling for characteristic: ${char.uuid}")

            serviceScope.launch {
                while (isConnected && dataCharacteristic != null) {
                    try {
                        Log.d(TAG, "📖 Attempting READ operation...")
                        readCharacteristic(char).with { _, data ->
                            val bytes = data.value
                            Log.d(TAG, "📖 READ SUCCESSFUL! Data null? ${bytes == null}")
                            if (bytes != null) {
                                val hexString = bytes.joinToString(" ") { "%02X".format(it) }
                                Log.d(TAG, "📡 RAW DATA READ (${bytes.size} bytes): $hexString")

                                val reading = SensorDataParser.parsePacket(bytes)
                                if (reading != null) {
                                    Log.d(TAG, "✅ Parsed voltage from READ: ${reading.voltage}")
                                    onReadingReceived(reading)
                                } else {
                                    Log.w(TAG, "❌ Invalid packet from READ")
                                }
                            }
                        }.fail { _, status ->
                            Log.w(TAG, "⚠️ READ failed with status: $status")
                        }.enqueue()

                        // Poll every 500ms
                        delay(500)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error during polling: ${e.message}")
                        break
                    }
                }
                Log.d(TAG, "📖 Polling stopped")
            }
        }

        override fun onServicesInvalidated() {
            dataCharacteristic = null
            onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)
        }
    }
}
