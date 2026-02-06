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
    private var classicBtManager: ClassicBluetoothManager? = null
    private var mockJob: Job? = null
    private var useMockMode = false
    private var scanner: BluetoothScanner? = null
    private var scanJob: Job? = null
    private var isClassicBluetooth = false
    private var pairingReceiver: PairingRequestReceiver? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _latestReading = MutableStateFlow<VoltageReading?>(null)
    val latestReading: StateFlow<VoltageReading?> = _latestReading.asStateFlow()

    private val _errorCount = MutableStateFlow(0)
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothScanner.ScannedDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothScanner.ScannedDevice>> = _discoveredDevices.asStateFlow()

    companion object {
        private const val TAG = "BluetoothService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "VoltageAlertService"

        // HM-10/JDY-08 standard UUIDs
        private val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        private val CHARACTERISTIC_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
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
        registerPairingReceiver()
    }

    /**
     * Register broadcast receiver to handle pairing requests.
     */
    private fun registerPairingReceiver() {
        pairingReceiver = PairingRequestReceiver()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            // Set high priority to intercept before system dialogs
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        registerReceiver(pairingReceiver, filter)
        Log.d(TAG, "Pairing receiver registered")
    }

    /**
     * Unregister pairing receiver.
     */
    private fun unregisterPairingReceiver() {
        pairingReceiver?.let {
            try {
                unregisterReceiver(it)
                pairingReceiver = null
                Log.d(TAG, "Pairing receiver unregistered")
            } catch (e: IllegalArgumentException) {
                // Already unregistered, ignore
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        val notification = createNotification("Initializing...")
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    /**
     * Connect to a Bluetooth device.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        serviceScope.launch {
            try {
                _connectionStatus.value = ConnectionStatus.CONNECTING

                // Determine if device is Classic Bluetooth or BLE
                // Classic BT devices have device.type == DEVICE_TYPE_CLASSIC (1)
                // BLE devices have device.type == DEVICE_TYPE_LE (2)
                // Dual mode devices have device.type == DEVICE_TYPE_DUAL (3)
                isClassicBluetooth = device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC ||
                                     device.type == BluetoothDevice.DEVICE_TYPE_DUAL

                Log.d(TAG, "Device type: ${device.type}, using ${if (isClassicBluetooth) "Classic BT" else "BLE"}")

                if (isClassicBluetooth) {
                    // For Classic Bluetooth, ensure device is paired first
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        Log.d(TAG, "Device not bonded, attempting legacy pairing...")

                        // Show PIN instructions to user (cannot auto-enter due to permission restrictions)
                        serviceScope.launch(Dispatchers.Main) {
                            PairingInstructionsHelper.showPinInstructions(
                                this@BluetoothService,
                                device.name ?: "ST9401-UP"
                            )
                        }

                        // Try legacy PIN-based pairing
                        val pairingStarted = LegacyPairingHelper.attemptLegacyPairing(device)

                        if (pairingStarted) {
                            Log.d(TAG, "Pairing initiated, waiting for bond...")
                            Log.d(TAG, "⚠️ Manual PIN entry required - try: 1234, 9527, 0000, 1111, 0001")

                            // Wait up to 30 seconds for pairing
                            var waitTime = 0
                            while (device.bondState != BluetoothDevice.BOND_BONDED && waitTime < 30000) {
                                delay(1000)
                                waitTime += 1000

                                if (device.bondState == BluetoothDevice.BOND_NONE) {
                                    Log.w(TAG, "Pairing failed or cancelled")
                                    break
                                }
                            }

                            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                                throw Exception("Failed to pair with device after 30 seconds")
                            }

                            Log.d(TAG, "Device successfully paired!")
                        } else {
                            throw Exception("Failed to initiate pairing")
                        }
                    } else {
                        Log.d(TAG, "Device already bonded")
                    }

                    // Use Classic Bluetooth (SPP)
                    classicBtManager = ClassicBluetoothManager(
                        onReadingReceived = { reading ->
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

                    classicBtManager?.connect(device)
                    Log.d(TAG, "Connecting via Classic Bluetooth: ${device.address}")

                } else {
                    // Use BLE (GATT)
                    bleManager = SensorBleManager(
                        context = this@BluetoothService,
                        onReadingReceived = { reading ->
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

                    bleManager?.connect(device)
                        ?.useAutoConnect(true)
                        ?.retry(3, 100)
                        ?.timeout(10000)
                        ?.suspend()

                    Log.d(TAG, "Connected via BLE: ${device.address}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _errorCount.value = _errorCount.value + 1

                // Attempt reconnection after delay
                delay(5000)
                if (_connectionStatus.value == ConnectionStatus.DISCONNECTED) {
                    connect(device)  // Retry
                }
            }
        }
    }

    /**
     * Disconnect from the current device.
     */
    fun disconnect() {
        serviceScope.launch {
            if (isClassicBluetooth) {
                classicBtManager?.disconnect()
                classicBtManager = null
            } else {
                bleManager?.disconnect()?.suspend()
                bleManager = null
            }
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
        updateNotification(ConnectionStatus.SCANNING)

        scanner?.startScan()

        // Observe scan results
        scanJob = serviceScope.launch {
            scanner?.discoveredDevices?.collect { devices ->
                _discoveredDevices.value = devices
                Log.d(TAG, "Discovered ${devices.size} devices")

                // Auto-connect to best device after finding some devices
                if (autoConnect && devices.isNotEmpty() && _connectionStatus.value == ConnectionStatus.SCANNING) {
                    delay(3000) // Wait 3 seconds to collect more devices

                    val bestDevice = scanner?.findBestDevice()
                    if (bestDevice != null) {
                        Log.d(TAG, "Auto-connecting to best device: ${bestDevice.name} (${bestDevice.device.address})")
                        stopScanning()
                        connect(bestDevice.device)
                    }
                }
            }
        }

        // Auto-stop scan after 10 seconds if no device found
        serviceScope.launch {
            delay(10000)
            if (_connectionStatus.value == ConnectionStatus.SCANNING) {
                Log.d(TAG, "Scan timeout - no devices found")
                stopScanning()
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                updateNotification(ConnectionStatus.DISCONNECTED)
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
            if (isClassicBluetooth) {
                classicBtManager?.cleanup()
            } else {
                bleManager?.disconnect()?.suspend()
            }
        }
        unregisterPairingReceiver()
        Log.d(TAG, "Service destroyed")
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
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Required service not found")
                return false
            }

            dataCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
            if (dataCharacteristic == null) {
                Log.e(TAG, "Required characteristic not found")
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
                setNotificationCallback(char).with { _, data ->
                    val bytes = data.value
                    if (bytes != null) {
                        val reading = SensorDataParser.parsePacket(bytes)
                        if (reading != null) {
                            onReadingReceived(reading)
                        } else {
                            Log.w(TAG, "Invalid packet received")
                            onError()
                        }
                    }
                }

                enableNotifications(char).enqueue()
            }

            onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        }

        override fun onServicesInvalidated() {
            dataCharacteristic = null
            onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)
        }
    }
}
