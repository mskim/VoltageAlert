package com.voltagealert.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import java.util.Locale
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.voltagealert.R
import com.voltagealert.alert.AlertCoordinator
import com.voltagealert.bluetooth.BluetoothPermissionHelper
import com.voltagealert.bluetooth.BluetoothService
import com.voltagealert.databinding.ActivityMainBinding
import com.voltagealert.models.ConnectionStatus
import com.voltagealert.models.VoltageLevel
import kotlinx.coroutines.launch

/**
 * Main activity for VoltageAlert.
 *
 * Responsibilities:
 * - Display connection status and current voltage
 * - Show event log
 * - Bind to BluetoothService for continuous monitoring
 * - Request runtime permissions
 * - Trigger alerts when dangerous voltage detected
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var bluetoothService: BluetoothService? = null
    private var isBound = false
    private lateinit var alertCoordinator: AlertCoordinator
    private val logAdapter = LogAdapter()
    private var lastAlertedVoltage: VoltageLevel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startBluetoothService()
        } else {
            showPermissionDeniedDialog()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            isBound = true
            observeBluetoothService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            isBound = false
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateBaseContextLocale(newBase, "ko"))
    }

    private fun updateBaseContextLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alertCoordinator = AlertCoordinator.getInstance(applicationContext)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()

        // Check permissions and start service
        checkPermissionsAndStart()
    }

    override fun onResume() {
        super.onResume()
        // Auto-scan when app comes to foreground if disconnected (Option B)
        bluetoothService?.let { service ->
            if (service.connectionStatus.value == ConnectionStatus.DISCONNECTED) {
                service.clearStatusMessage()
                // Auto-start scanning
                Log.d("MainActivity", "🔍 Auto-starting scan (app resumed, disconnected)")
                service.startScanning(autoConnect = true)
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvEventLog.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnClearLogs.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_logs)
                .setMessage("Clear all event logs?")
                .setPositiveButton(R.string.ok) { _, _ ->
                    viewModel.clearLogs()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.fabSettings.setOnClickListener {
            // TODO: Open SettingsActivity
        }

        // Scan button - start Bluetooth scanning
        binding.btnScan.setOnClickListener {
            bluetoothService?.startScanning(autoConnect = true)
        }

        // Mock mode toggle
        binding.switchMockMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                bluetoothService?.startMockMode(com.voltagealert.testing.MockBluetoothDevice.Scenario.MIXED)
            } else {
                bluetoothService?.stopMockMode()
            }
        }

        // Test button click listeners
        binding.btnTest220V.setOnClickListener { testAlert(com.voltagealert.models.VoltageLevel.VOLTAGE_220V) }
        binding.btnTest380V.setOnClickListener { testAlert(com.voltagealert.models.VoltageLevel.VOLTAGE_380V) }
        binding.btnTest154KV.setOnClickListener { testAlert(com.voltagealert.models.VoltageLevel.VOLTAGE_154KV) }
        binding.btnTest229KV.setOnClickListener { testAlert(com.voltagealert.models.VoltageLevel.VOLTAGE_229KV) }
        binding.btnTest345KV.setOnClickListener { testAlert(com.voltagealert.models.VoltageLevel.VOLTAGE_345KV) }
        binding.btnTest500KV.setOnClickListener { testAlert(com.voltagealert.models.VoltageLevel.VOLTAGE_500KV) }
        binding.btnTest765KV.setOnClickListener { testAlert(com.voltagealert.models.VoltageLevel.VOLTAGE_765KV) }
    }

    private fun testAlert(voltageLevel: com.voltagealert.models.VoltageLevel) {
        android.util.Log.d("MainActivity", "🧪 TEST BUTTON CLICKED: $voltageLevel")
        // Trigger alert directly without logging
        alertCoordinator.triggerAlert(voltageLevel)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe connection status
                launch {
                    viewModel.connectionStatus.collect { status ->
                        updateConnectionStatus(status)
                    }
                }

                // Observe status message
                launch {
                    viewModel.statusMessage.collect { message ->
                        if (message.isNotEmpty()) {
                            binding.tvStatusMessage.text = message
                            binding.tvStatusMessage.visibility = View.VISIBLE
                        } else {
                            binding.tvStatusMessage.visibility = View.GONE
                        }
                    }
                }

                // Observe latest reading
                launch {
                    viewModel.latestReading.collect { reading ->
                        if (reading != null) {
                            val voltageText = getString(reading.voltage.displayNameRes)
                            android.util.Log.d("MainActivity", "📱 UI updating voltage display: $voltageText")
                            binding.tvCurrentVoltage.text = voltageText

                            // Change card color: yellow for low voltage, red for high voltage
                            val cardColor = when (reading.voltage) {
                                VoltageLevel.VOLTAGE_220V, VoltageLevel.VOLTAGE_380V ->
                                    ContextCompat.getColor(this@MainActivity, R.color.warning_yellow)
                                else ->
                                    ContextCompat.getColor(this@MainActivity, R.color.danger_red)
                            }
                            binding.voltageCard.setStrokeColor(cardColor)

                            // Trigger alert if dangerous AND different from last alerted voltage
                            if (reading.voltage.isDangerous && reading.voltage != lastAlertedVoltage) {
                                lastAlertedVoltage = reading.voltage
                                alertCoordinator.triggerAlert(reading.voltage)
                            } else if (!reading.voltage.isDangerous) {
                                // Reset last alerted voltage when safe voltage detected
                                lastAlertedVoltage = null
                            }
                        } else {
                            binding.tvCurrentVoltage.text = getString(R.string.no_voltage_detected)
                            // Reset to red when no voltage
                            binding.voltageCard.setStrokeColor(ContextCompat.getColor(this@MainActivity, R.color.danger_red))
                            lastAlertedVoltage = null

                            // Auto-stop any active alert (sensor stopped sending)
                            if (alertCoordinator.isAlertActive()) {
                                Log.d("MainActivity", "🔇 Auto-stopping alert (no voltage data)")
                                alertCoordinator.stopAllAlerts()
                            }
                        }
                    }
                }

                // Observe log entries
                launch {
                    viewModel.logEntries.collect { entries ->
                        logAdapter.submitList(entries)

                        // Show/hide empty state
                        if (entries.isEmpty()) {
                            binding.rvEventLog.visibility = View.GONE
                            binding.tvEmptyState.visibility = View.VISIBLE
                        } else {
                            binding.rvEventLog.visibility = View.VISIBLE
                            binding.tvEmptyState.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun observeBluetoothService() {
        bluetoothService?.let { service ->
            // Auto-start scanning if disconnected (Option B: Auto-scan on app start)
            if (service.connectionStatus.value == ConnectionStatus.DISCONNECTED) {
                Log.d("MainActivity", "🔍 Auto-starting scan (disconnected)")
                service.startScanning(autoConnect = true)
            }

            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    // Observe connection status
                    launch {
                        service.connectionStatus.collect { status ->
                            viewModel.updateConnectionStatus(status)
                        }
                    }

                    // Observe readings
                    launch {
                        service.latestReading.collect { reading ->
                            reading?.let {
                                viewModel.updateReading(it)
                            }
                        }
                    }

                    // Observe status message
                    launch {
                        service.statusMessage.collect { message ->
                            viewModel.updateStatusMessage(message)
                        }
                    }
                }
            }
        }
    }

    private fun updateConnectionStatus(status: ConnectionStatus) {
        val (statusText, statusColor) = when (status) {
            ConnectionStatus.CONNECTED -> Pair(
                getString(R.string.status_connected),
                ContextCompat.getColor(this, R.color.status_connected)
            )
            ConnectionStatus.CONNECTING -> Pair(
                getString(R.string.status_connecting),
                ContextCompat.getColor(this, R.color.status_connecting)
            )
            ConnectionStatus.SCANNING -> Pair(
                getString(R.string.status_scanning),
                ContextCompat.getColor(this, R.color.status_connecting)
            )
            ConnectionStatus.DISCONNECTED -> Pair(
                getString(R.string.status_disconnected),
                ContextCompat.getColor(this, R.color.status_disconnected)
            )
        }

        binding.tvConnectionStatus.text = statusText
        binding.statusIndicator.setBackgroundColor(statusColor)
    }

    private fun checkPermissionsAndStart() {
        val missingPermissions = BluetoothPermissionHelper.getMissingPermissions(this).toMutableList()

        // Check notification permission (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (missingPermissions.isEmpty()) {
            startBluetoothService()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startBluetoothService() {
        val intent = Intent(this, BluetoothService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_bluetooth_title)
            .setMessage(R.string.permission_bluetooth_message)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                checkPermissionsAndStart()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
