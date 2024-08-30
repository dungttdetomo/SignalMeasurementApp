package com.example.signalmeasurementapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SignalMeasurementDao {

    @Insert
    suspend fun insert(signalMeasurement: SignalMeasurement)

    @Query("SELECT * FROM signal_measurements")
    suspend fun getAllMeasurements(): List<SignalMeasurement>
}
