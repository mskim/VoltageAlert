package com.voltagealert.logging

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.voltagealert.models.VoltageLevel
import java.time.LocalDateTime

/**
 * Room entity for storing voltage detection events.
 */
@Entity(tableName = "voltage_logs")
@TypeConverters(Converters::class)
data class VoltageLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** When the voltage was detected */
    val timestamp: LocalDateTime,

    /** The voltage level that was detected */
    val voltageLevel: VoltageLevel,

    /** Raw voltage value for display (e.g., "220V", "154KV") */
    val rawValue: String,

    /** True if this entry was suppressed due to duplicate detection rule */
    val isSuppressed: Boolean = false
)
