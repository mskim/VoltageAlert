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
import com.voltagealert.alert.AlertCoordinator
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
    private var broadcastJob: Job? = null
    private var scanCacheFlushJob: Job? = null
    private var autoConnectJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private var readingTimeoutJob: Job? = null

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
     * Connect to BLE voltage sensor device (direct connection from scan results).
     * ST9401-UP uses ESP32-S3 with NimBLE - BLE with "Just Works" pairing (no PIN).
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        serviceScope.launch {
            try {
                _connectionStatus.value = ConnectionStatus.CONNECTING
                Log.d(TAG, "Connecting to BLE device: ${device.name} (${device.address})")

                cleanupBleManager()
                bleManager = createBleManager()

                // useAutoConnect(false) = direct connection (fast, device must be advertising now)
                bleManager?.connect(device)
                    ?.useAutoConnect(false)
                    ?.retry(3, 100)
                    ?.timeout(10000)
                    ?.suspend()

                Log.d(TAG, "✓ Connected to BLE device: ${device.address}")
                saveLastConnectedDevice(device.address)

            } catch (e: Exception) {
                Log.e(TAG, "BLE connection failed", e)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _errorCount.value = _errorCount.value + 1
            }
        }
    }

    /**
     * Fast reconnect: immediately start active scanning for saved device.
     * Active scanning uses HIGH duty cycle (finds device in 100-500ms).
     * autoConnect(true) was SLOWER because it uses low duty cycle background scanning.
     */
    private fun fastReconnect() {
        Log.d(TAG, "⚡ Fast reconnect: immediate active scan")
        _statusMessage.value = "Reconnecting..."
        startScanning(autoConnect = true)
    }

    /**
     * Create a new SensorBleManager with shared callbacks.
     * Centralizes the reading timeout, alert auto-stop, and disconnect handling.
     */
    private fun createBleManager(): SensorBleManager {
        return SensorBleManager(
            context = this@BluetoothService,
            onReadingReceived = { reading ->
                Log.d(TAG, "🔄 Emitting reading to UI: ${reading.voltage}")
                _latestReading.value = reading
                _errorCount.value = 0

                // Restart 2-second reading timeout
                // When sensor stops sending, this fires and clears the reading + stops alarm
                readingTimeoutJob?.cancel()
                readingTimeoutJob = serviceScope.launch {
                    delay(2000)
                    Log.d(TAG, "⏱️ Reading timeout - sensor stopped sending")
                    _latestReading.value = null
                    // Stop alarm directly from service (MainActivity might be paused behind AlertActivity)
                    stopAlertFromService()
                }
            },
            onConnectionStatusChanged = { status ->
                _connectionStatus.value = status
                updateNotification(status)
                if (status == ConnectionStatus.DISCONNECTED) {
                    // Clear reading and stop alarm immediately on disconnect
                    readingTimeoutJob?.cancel()
                    _latestReading.value = null
                    stopAlertFromService()
                    Log.d(TAG, "🔴 BLE disconnected - cleared reading, stopped alarm")
                    if (!useMockMode) {
                        autoRescanOnDisconnect()
                    }
                }
            },
            onError = {
                _errorCount.value = _errorCount.value + 1
            }
        )
    }

    /**
     * Stop any active alert directly from the service.
     * This is needed because MainActivity may be paused (behind AlertActivity)
     * and its lifecycle-bound collectors won't run.
     */
    private fun stopAlertFromService() {
        try {
            val coordinator = AlertCoordinator.getInstance(this@BluetoothService)
            if (coordinator.isAlertActive()) {
                Log.d(TAG, "🔇 Auto-stopping alert from service")
                coordinator.stopAllAlerts()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop alert: ${e.message}")
        }
    }

    /**
     * Properly clean up old BLE manager to release GATT resources.
     * Without this, Android BLE stack blocks reconnection to the same device.
     */
    private suspend fun cleanupBleManager() {
        bleManager?.let { oldManager ->
            Log.d(TAG, "🧹 Cleaning up old BLE manager...")
            try {
                withTimeout(2000) {
                    oldManager.disconnect().suspend()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Old manager disconnect: ${e.message}")
            }
            try {
                oldManager.close()
            } catch (e: Exception) {
                Log.d(TAG, "Old manager close: ${e.message}")
            }
            bleManager = null
            Log.d(TAG, "🧹 Old BLE manager cleaned up")
        }
    }

    /**
     * Disconnect from the current BLE device.
     */
    fun disconnect() {
        serviceScope.launch {
            readingTimeoutJob?.cancel()
            cleanupBleManager()
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
     * Broadcast-only mode: reads voltage data directly from BLE advertisements.
     * No GATT connection needed - scan → read → alarm in 100-500ms.
     */
    fun startScanning(autoConnect: Boolean = true) {
        // If scan is already running, don't restart (avoids Android scan throttling)
        if (scanner?.isScanning?.value == true && broadcastJob?.isActive == true) {
            Log.d(TAG, "⚡ Scan already running - skipping restart")
            return
        }

        Log.d(TAG, "Starting BLE broadcast scan")

        // Cancel previous coroutines (but minimize scan stop/start cycles)
        scanJob?.cancel()
        scanJob = null
        broadcastJob?.cancel()
        broadcastJob = null
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null

        if (useMockMode) {
            stopMockMode()
        }

        _connectionStatus.value = ConnectionStatus.SCANNING
        _statusMessage.value = "Scanning..."
        _discoveredDevices.value = emptyList()
        updateNotification(ConnectionStatus.SCANNING)

        // Only stop/start the actual BLE scan if it's not already running
        if (scanner?.isScanning?.value != true) {
            scanner?.startScan()
        }

        // Broadcast mode: collect voltage readings from advertisement data.
        // No GATT connection. Scan → read advertisement → alarm (100-500ms).
        broadcastJob = serviceScope.launch {
            scanner?.broadcastReading?.collect { reading ->
                Log.d(TAG, "⚡ BROADCAST: ${reading.voltage}")
                _latestReading.value = reading
                _errorCount.value = 0

                // Update status on first broadcast received
                if (_connectionStatus.value == ConnectionStatus.SCANNING) {
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    _statusMessage.value = "Monitoring"
                    updateNotification(ConnectionStatus.CONNECTED)
                }

                // Restart 2-second reading timeout
                // When sensor stops broadcasting, this clears the reading + stops alarm
                readingTimeoutJob?.cancel()
                readingTimeoutJob = serviceScope.launch {
                    delay(2000)
                    Log.d(TAG, "⏱️ Broadcast timeout - sensor stopped")
                    _latestReading.value = null
                    stopAlertFromService()
                    // Flush BLE scan cache immediately when sensor stops broadcasting.
                    // Samsung/Android caches scan results and reduces callback frequency
                    // for known devices after ~6 results. Flushing here ensures the NEXT
                    // voltage detection is reported instantly (no 6th-detection delay).
                    if (scanner?.isScanning?.value == true) {
                        scanner?.flushScanCache()
                        Log.d(TAG, "🔄 Flushed scan cache on sensor idle")
                    }
                    // Revert status to scanning (scan is still running)
                    if (scanner?.isScanning?.value == true) {
                        _connectionStatus.value = ConnectionStatus.SCANNING
                        _statusMessage.value = "Scanning..."
                        updateNotification(ConnectionStatus.SCANNING)
                    }
                }
            }
        }

        // Periodic BLE scan cache flush (every 20 seconds).
        // Samsung/Android reduces scan callback frequency for known devices over time.
        // Flushing resets this so every advertisement is reported promptly.
        // 20s interval = max 1-2 flushes per 30s, well under Android's 5-per-30s limit.
        scanCacheFlushJob?.cancel()
        scanCacheFlushJob = serviceScope.launch {
            while (true) {
                delay(20000)
                if (scanner?.isScanning?.value == true) {
                    scanner?.flushScanCache()
                }
            }
        }

        // Track discovered devices for UI
        scanJob = serviceScope.launch {
            scanner?.discoveredDevices?.collect { devices ->
                _discoveredDevices.value = devices
            }
        }

        // No scan timeout - broadcast mode runs continuously until user stops
    }

    /**
     * Stop all scanning and related coroutines.
     */
    fun stopScanning() {
        Log.d(TAG, "Stopping scan and all related coroutines")
        scanner?.stopScan()
        scanJob?.cancel()
        scanJob = null
        broadcastJob?.cancel()
        broadcastJob = null
        scanCacheFlushJob?.cancel()
        scanCacheFlushJob = null
        autoConnectJob?.cancel()
        autoConnectJob = null
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        autoRescanJob?.cancel()
        autoRescanJob = null
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
        readingTimeoutJob?.cancel()
        broadcastJob?.cancel()
        scanCacheFlushJob?.cancel()
        autoRescanJob?.cancel()
        serviceScope.launch {
            cleanupBleManager()
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

    /**
     * Auto-restart scan when BLE connection drops (legacy GATT path).
     * In broadcast mode, scan is already running - just update status.
     */
    private var autoRescanJob: Job? = null

    private fun autoRescanOnDisconnect() {
        autoRescanJob?.cancel()
        autoRescanJob = serviceScope.launch {
            if (_connectionStatus.value == ConnectionStatus.DISCONNECTED && !useMockMode) {
                // Scan is already running in broadcast mode - just update status
                if (scanner?.isScanning?.value == true) {
                    Log.d(TAG, "⚡ Scan still running - ready for next broadcast")
                    _connectionStatus.value = ConnectionStatus.SCANNING
                    _statusMessage.value = "Scanning..."
                    updateNotification(ConnectionStatus.SCANNING)
                } else {
                    startScanning()
                }
            }
        }
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
        // Track when last NOTIFY was received to detect stale READ data
        private var lastNotifyTime = 0L

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

            lastReadingTime = System.currentTimeMillis()  // Reset watchdog timer

            dataCharacteristic?.let { char ->
                Log.d(TAG, "🔔 Setting up notification callback for characteristic: ${char.uuid}")

                setNotificationCallback(char).with { _, data ->
                    val bytes = data.value
                    if (bytes != null) {
                        val hexString = bytes.joinToString(" ") { "%02X".format(it) }
                        Log.d(TAG, "📡 RAW DATA RECEIVED via NOTIFY (${bytes.size} bytes): $hexString")

                        val reading = SensorDataParser.parsePacket(bytes)
                        if (reading != null) {
                            Log.d(TAG, "✅ Parsed voltage from NOTIFY: ${reading.voltage}")
                            lastNotifyTime = System.currentTimeMillis()
                            lastReadingTime = System.currentTimeMillis()
                            onReadingReceived(reading)
                        } else {
                            Log.w(TAG, "❌ Invalid packet received - parsing failed")
                            onError()
                        }
                    }
                }

                Log.d(TAG, "🔔 Enabling notifications for characteristic: ${char.uuid}")
                enableNotifications(char).done {
                    Log.d(TAG, "✅ Notifications ENABLED successfully on ${char.uuid}")
                    // Start polling via READ as well (manufacturer hint shows READ + NOTIFY)
                    startPollingCharacteristic(char)
                    // Start watchdog to recover from silent data loss
                    startWatchdog()
                }.fail { device, status ->
                    Log.e(TAG, "❌ Failed to enable notifications: status=$status")
                    // Even if notifications fail, start polling - READ may still work
                    startPollingCharacteristic(char)
                    startWatchdog()
                }.enqueue()
            }

            onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        }

        private var pollingJob: Job? = null

        private fun startPollingCharacteristic(char: BluetoothGattCharacteristic) {
            Log.d(TAG, "📖 Starting READ polling for characteristic: ${char.uuid}")

            // Cancel any existing polling job before starting a new one
            pollingJob?.cancel()

            pollingJob = serviceScope.launch {
                var consecutiveErrors = 0
                val maxConsecutiveErrors = 30  // Allow up to 30 errors (15 seconds at 500ms interval)

                while (isConnected && dataCharacteristic != null) {
                    try {
                        readCharacteristic(char).with { _, data ->
                            val bytes = data.value
                            if (bytes != null) {
                                val hexString = bytes.joinToString(" ") { "%02X".format(it) }
                                Log.d(TAG, "📡 RAW DATA READ (${bytes.size} bytes): $hexString")

                                val reading = SensorDataParser.parsePacket(bytes)
                                if (reading != null) {
                                    // Only emit READ data if NOTIFY was recent (within 3 seconds)
                                    // This prevents stale cached data from keeping the alarm alive
                                    val timeSinceNotify = System.currentTimeMillis() - lastNotifyTime
                                    if (timeSinceNotify < 3000) {
                                        Log.d(TAG, "✅ Parsed voltage from READ: ${reading.voltage} (notify ${timeSinceNotify}ms ago)")
                                        lastReadingTime = System.currentTimeMillis()
                                        onReadingReceived(reading)
                                        consecutiveErrors = 0  // Reset error counter on success
                                    } else {
                                        Log.d(TAG, "⏭️ Skipping stale READ data (last notify ${timeSinceNotify}ms ago)")
                                    }
                                }
                            }
                        }.fail { _, status ->
                            Log.w(TAG, "⚠️ READ failed with status: $status")
                            consecutiveErrors++
                        }.enqueue()

                        delay(500)
                    } catch (e: Exception) {
                        consecutiveErrors++
                        Log.w(TAG, "⚠️ Polling error ($consecutiveErrors/$maxConsecutiveErrors): ${e.message}")

                        if (consecutiveErrors >= maxConsecutiveErrors) {
                            Log.e(TAG, "❌ Too many consecutive polling errors, attempting recovery...")
                            // Try to re-enable notifications as recovery
                            try {
                                dataCharacteristic?.let { currentChar ->
                                    enableNotifications(currentChar).enqueue()
                                    Log.d(TAG, "🔄 Re-enabled notifications for recovery")
                                }
                            } catch (re: Exception) {
                                Log.e(TAG, "Recovery failed: ${re.message}")
                            }
                            consecutiveErrors = 0  // Reset and keep trying
                        }

                        // Wait before retrying (don't break the loop!)
                        delay(2000)
                    }
                }
                Log.d(TAG, "📖 Polling stopped (isConnected=$isConnected, char=${dataCharacteristic != null})")
            }
        }

        /**
         * Watchdog: periodically checks if data is flowing and attempts recovery if not.
         */
        private var watchdogJob: Job? = null
        private var lastReadingTime = System.currentTimeMillis()

        private fun startWatchdog() {
            watchdogJob?.cancel()
            watchdogJob = serviceScope.launch {
                while (isConnected) {
                    delay(15000)  // Check every 15 seconds

                    val timeSinceLastReading = System.currentTimeMillis() - lastReadingTime
                    if (timeSinceLastReading > 15000 && isConnected && dataCharacteristic != null) {
                        Log.w(TAG, "🐕 Watchdog: No data for ${timeSinceLastReading/1000}s, attempting recovery...")

                        try {
                            dataCharacteristic?.let { char ->
                                // Re-enable notifications
                                enableNotifications(char).enqueue()
                                Log.d(TAG, "🐕 Watchdog: Re-enabled notifications")

                                // Restart polling if job is not active
                                if (pollingJob?.isActive != true) {
                                    Log.d(TAG, "🐕 Watchdog: Restarting polling loop")
                                    startPollingCharacteristic(char)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "🐕 Watchdog recovery failed: ${e.message}")
                        }
                    }
                }
                Log.d(TAG, "🐕 Watchdog stopped")
            }
        }

        override fun onServicesInvalidated() {
            Log.d(TAG, "🔴 Services invalidated - cleaning up polling and watchdog")
            pollingJob?.cancel()
            pollingJob = null
            watchdogJob?.cancel()
            watchdogJob = null
            dataCharacteristic = null
            onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)
        }
    }
}
