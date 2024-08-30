package com.example.signalmeasurementapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SignalMeasurement::class], version = 1, exportSchema = false)
abstract class SignalMeasurementDatabase : RoomDatabase() {

    abstract fun signalMeasurementDao(): SignalMeasurementDao

    companion object {
        @Volatile
        private var INSTANCE: SignalMeasurementDatabase? = null

        fun getDatabase(context: Context): SignalMeasurementDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SignalMeasurementDatabase::class.java,
                    "signal_measurement_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
