package com.example.signalmeasurementapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_measurements")
data class SignalMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val signalStrength: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
