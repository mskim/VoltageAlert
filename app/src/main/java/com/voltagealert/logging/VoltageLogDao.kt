package com.voltagealert.logging

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for voltage log operations.
 */
@Dao
interface VoltageLogDao {
    /**
     * Get all logs (limited to 99 most recent), ordered by timestamp descending.
     */
    @Query("SELECT * FROM voltage_logs ORDER BY timestamp DESC LIMIT 99")
    fun getAllLogs(): Flow<List<VoltageLogEntity>>

    /**
     * Get only visible logs (not suppressed), ordered by timestamp descending.
     */
    @Query("SELECT * FROM voltage_logs WHERE isSuppressed = 0 ORDER BY timestamp DESC LIMIT 99")
    fun getVisibleLogs(): Flow<List<VoltageLogEntity>>

    /**
     * Insert a new log entry.
     */
    @Insert
    suspend fun insertLog(log: VoltageLogEntity): Long

    /**
     * Get the total count of log entries.
     */
    @Query("SELECT COUNT(*) FROM voltage_logs")
    suspend fun getLogCount(): Int

    /**
     * Delete the oldest log entry (by timestamp).
     */
    @Query("DELETE FROM voltage_logs WHERE id = (SELECT id FROM voltage_logs ORDER BY timestamp ASC LIMIT 1)")
    suspend fun deleteOldest()

    /**
     * Clear all logs.
     */
    @Query("DELETE FROM voltage_logs")
    suspend fun clearAll()

    /**
     * Get the last N log entries.
     */
    @Query("SELECT * FROM voltage_logs ORDER BY timestamp DESC LIMIT :count")
    suspend fun getLastNLogs(count: Int): List<VoltageLogEntity>
}
