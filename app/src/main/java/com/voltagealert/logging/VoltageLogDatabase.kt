package com.voltagealert.logging

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for voltage logs.
 */
@Database(
    entities = [VoltageLogEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VoltageLogDatabase : RoomDatabase() {
    abstract fun voltageLogDao(): VoltageLogDao

    companion object {
        @Volatile
        private var INSTANCE: VoltageLogDatabase? = null

        fun getInstance(context: Context): VoltageLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoltageLogDatabase::class.java,
                    "voltage_logs.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
