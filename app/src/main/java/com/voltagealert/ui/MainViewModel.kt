package com.voltagealert.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voltagealert.logging.VoltageLogEntry
import com.voltagealert.logging.VoltageLogManager
import com.voltagealert.models.ConnectionStatus
import com.voltagealert.models.VoltageReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for MainActivity.
 *
 * Exposes StateFlows for:
 * - Connection status
 * - Latest voltage reading
 * - Event log entries
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val logManager = VoltageLogManager(application)

    // Connection status from BluetoothService
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    // Latest voltage reading from BluetoothService
    private val _latestReading = MutableStateFlow<VoltageReading?>(null)
    val latestReading: StateFlow<VoltageReading?> = _latestReading.asStateFlow()

    // Event log entries
    private val _logEntries = MutableStateFlow<List<VoltageLogEntry>>(emptyList())
    val logEntries: StateFlow<List<VoltageLogEntry>> = _logEntries.asStateFlow()

    // Status message from BluetoothService
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    init {
        // Observe log entries from database
        viewModelScope.launch {
            logManager.getVisibleLogs().collect { entries ->
                _logEntries.value = entries
            }
        }
    }

    /**
     * Update connection status (called by MainActivity when BluetoothService reports changes).
     */
    fun updateConnectionStatus(status: ConnectionStatus) {
        _connectionStatus.value = status
    }

    /**
     * Update status message (called by MainActivity when BluetoothService reports changes).
     */
    fun updateStatusMessage(message: String) {
        _statusMessage.value = message
    }

    /**
     * Update latest reading and log it (called by MainActivity when BluetoothService reports new reading).
     */
    fun updateReading(reading: VoltageReading) {
        _latestReading.value = reading

        // Log the reading
        viewModelScope.launch {
            logManager.insertReading(reading)
        }
    }

    /**
     * Clear latest reading (sensor stopped sending or BLE disconnected).
     * Resets the reading so the next detection triggers a fresh alarm.
     * Also resets the duplicate filter so the next detection logs fresh.
     */
    fun clearReading() {
        _latestReading.value = null
        logManager.resetDuplicateFilter()
    }

    /**
     * Clear all log entries.
     */
    fun clearLogs() {
        viewModelScope.launch {
            logManager.clearAllLogs()
        }
    }
}
