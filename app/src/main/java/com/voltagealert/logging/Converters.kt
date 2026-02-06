package com.voltagealert.logging

import androidx.room.TypeConverter
import com.voltagealert.models.VoltageLevel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Room type converters for custom types.
 */
class Converters {
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun fromTimestamp(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it, dateTimeFormatter) }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): String? {
        return date?.format(dateTimeFormatter)
    }

    @TypeConverter
    fun fromVoltageLevel(value: VoltageLevel?): String? {
        return value?.name
    }

    @TypeConverter
    fun toVoltageLevel(value: String?): VoltageLevel? {
        return value?.let { VoltageLevel.valueOf(it) }
    }
}
