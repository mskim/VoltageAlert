package com.voltagealert.logging

import android.content.Context
import com.voltagealert.models.VoltageLevel
import com.voltagealert.models.VoltageReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.os.Environment
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Business logic for managing voltage logs.
 * Handles duplicate suppression, 99-entry limit, and formatting.
 */
class VoltageLogManager(private val context: Context) {
    private val database = VoltageLogDatabase.getInstance(context)
    private val dao = database.voltageLogDao()
    private val duplicateFilter = DuplicateSuppressionFilter()

    // Track last logged reading to skip same-second duplicates
    private var lastLoggedVoltage: VoltageLevel? = null
    private var lastLoggedSecond: Long = 0L

    companion object {
        private const val MAX_LOG_ENTRIES = 99
        private val LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    }

    /**
     * Insert a voltage reading into the log.
     * Applies same-second dedup, duplicate suppression, and maintains 99-entry limit.
     *
     * @param reading The voltage reading to log
     */
    suspend fun insertReading(reading: VoltageReading) {
        // Skip if same voltage in the same second (prevents multiple logs per second)
        val readingSecond = reading.timestamp.toEpochSecond(java.time.ZoneOffset.UTC)
        if (reading.voltage == lastLoggedVoltage && readingSecond == lastLoggedSecond) {
            return
        }
        lastLoggedVoltage = reading.voltage
        lastLoggedSecond = readingSecond

        // Check duplicate suppression
        val shouldLog = duplicateFilter.shouldLogReading(reading.voltage)

        // Create log entity
        val logEntity = VoltageLogEntity(
            timestamp = reading.timestamp,
            voltageLevel = reading.voltage,
            rawValue = getVoltageDisplayString(reading.voltage),
            isSuppressed = !shouldLog
        )

        // Enforce 99-entry limit (FIFO)
        val currentCount = dao.getLogCount()
        if (currentCount >= MAX_LOG_ENTRIES) {
            dao.deleteOldest()
        }

        // Insert the log
        dao.insertLog(logEntity)
    }

    /**
     * Get all visible (non-suppressed) logs as a Flow.
     */
    fun getVisibleLogs(): Flow<List<VoltageLogEntry>> {
        return dao.getVisibleLogs().map { entities ->
            entities.mapIndexed { index, entity ->
                VoltageLogEntry(
                    sequenceNumber = entities.size - index,
                    timestamp = entity.timestamp.format(LOG_DATE_FORMAT),
                    voltage = getVoltageDisplayString(entity.voltageLevel)
                )
            }
        }
    }

    /**
     * Get all logs (including suppressed) as a Flow.
     */
    fun getAllLogs(): Flow<List<VoltageLogEntry>> {
        return dao.getAllLogs().map { entities ->
            entities.mapIndexed { index, entity ->
                VoltageLogEntry(
                    sequenceNumber = entities.size - index,
                    timestamp = entity.timestamp.format(LOG_DATE_FORMAT),
                    voltage = getVoltageDisplayString(entity.voltageLevel),
                    isSuppressed = entity.isSuppressed
                )
            }
        }
    }

    /**
     * Reset the duplicate suppression filter.
     * Call this when sensor stops sending (disconnect/timeout) so that
     * the next detection of the same voltage is logged fresh.
     */
    fun resetDuplicateFilter() {
        duplicateFilter.reset()
        lastLoggedVoltage = null
        lastLoggedSecond = 0L
    }

    /**
     * Clear all logs and reset duplicate filter.
     */
    suspend fun clearAllLogs() {
        dao.clearAll()
        duplicateFilter.reset()
    }

    /**
     * Save all visible logs to a file in the phone's root storage.
     * File format: HVPA#yyyyMMdd_HHmmss.log
     * Location: /storage/emulated/0/HVPA/
     *
     * @param bleDebugLog Optional BLE scan debug log lines to append
     * @return The saved file path, or null if failed
     */
    suspend fun saveLogsToFile(bleDebugLog: List<String>? = null): String? {
        val entries = getVisibleLogs().first()
        if (entries.isEmpty() && bleDebugLog.isNullOrEmpty()) return null

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = "HVPA#$timestamp.log"

        // Build log content: event log + BLE debug log
        val content = buildString {
            if (entries.isNotEmpty()) {
                appendLine("=== Event Log ===")
                entries.forEach { appendLine(it.getFormattedDisplay()) }
            }
            if (!bleDebugLog.isNullOrEmpty()) {
                appendLine()
                appendLine("=== BLE Debug Log ===")
                bleDebugLog.forEach { appendLine(it) }
            }
        }

        // Save to /storage/emulated/0/HVPA/
        val hvpaDir = File(Environment.getExternalStorageDirectory(), "HVPA")
        if (!hvpaDir.exists()) {
            hvpaDir.mkdirs()
        }
        val file = File(hvpaDir, fileName)

        return try {
            file.writeText(content)
            file.absolutePath
        } catch (e: Exception) {
            // Fallback to app-specific directory if root storage fails
            val fallbackDir = context.getExternalFilesDir(null) ?: return null
            val fallbackFile = File(fallbackDir, fileName)
            try {
                fallbackFile.writeText(content)
                fallbackFile.absolutePath
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Get voltage display string (e.g., "220V", "154KV").
     */
    private fun getVoltageDisplayString(voltage: VoltageLevel): String {
        return when (voltage) {
            VoltageLevel.VOLTAGE_220V -> "220V"
            VoltageLevel.VOLTAGE_380V -> "380V"
            VoltageLevel.VOLTAGE_229KV -> "22.9KV"
            VoltageLevel.VOLTAGE_154KV -> "154KV"
            VoltageLevel.VOLTAGE_345KV -> "345KV"
            VoltageLevel.VOLTAGE_500KV -> "500KV"
            VoltageLevel.VOLTAGE_765KV -> "765KV"
            VoltageLevel.DIAGNOSTIC_OK -> "DIAG_OK"
            VoltageLevel.DIAGNOSTIC_NG -> "DIAG_NG"
        }
    }
}

/**
 * Formatted log entry for display.
 */
data class VoltageLogEntry(
    val sequenceNumber: Int,
    val timestamp: String,
    val voltage: String,
    val isSuppressed: Boolean = false
) {
    /**
     * Format for display: "1. 2025/12/23 08:45:25 220V"
     */
    fun getFormattedDisplay(): String {
        return "$sequenceNumber. $timestamp $voltage"
    }
}
