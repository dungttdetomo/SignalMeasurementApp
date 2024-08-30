package com.example.signalmeasurementapp

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.signalmeasurementapp.data.SignalMeasurement
import com.example.signalmeasurementapp.data.SignalMeasurementDatabase
import com.example.signalmeasurementapp.data.SignalMeasurementApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SignalMeasurementService : Service() {

    private val binder = LocalBinder()

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var locationManager: LocationManager
    private lateinit var database: SignalMeasurementDatabase
    private lateinit var api: SignalMeasurementApi

    // StateFlow to hold the latest measurement data
    private val _measurementFlow = MutableStateFlow(SignalMeasurement(0, 0, 0.0, 0.0, 0L))
    val measurementFlow: StateFlow<SignalMeasurement> = _measurementFlow

    inner class LocalBinder : Binder() {
        fun getService(): SignalMeasurementService = this@SignalMeasurementService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        database = SignalMeasurementDatabase.getDatabase(applicationContext)

        // Initialize Retrofit for server communication
        val retrofit = Retrofit.Builder()
            .baseUrl("https://yourserver.com/api/") // Replace with your server URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(SignalMeasurementApi::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check for location permissions before measuring signal strength
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            measureSignalStrength()
        } else {
            // Stop the service if permissions are not granted
            stopSelf()
        }
        return START_STICKY
    }

    private fun measureSignalStrength() {
        val signalStrength: SignalStrength? = telephonyManager.signalStrength
        val location: Location? = getLastKnownLocation()

        if (location != null && signalStrength != null) {
            val latitude = location.latitude
            val longitude = location.longitude

            val measurement = SignalMeasurement(
                signalStrength = signalStrength.level, // Assuming `level` gives the correct signal strength value
                latitude = latitude,
                longitude = longitude,
                timestamp = System.currentTimeMillis()
            )

            // Emit the latest measurement to the StateFlow
            Log.d("SignalMeasurementService", "Measurement before update: ${_measurementFlow.value}")
            _measurementFlow.value = measurement
            Log.d("SignalMeasurementService", "Measurement updated in StateFlow: ${_measurementFlow.value}")

            CoroutineScope(Dispatchers.IO).launch {
                // Insert the measurement into the database
                database.signalMeasurementDao().insert(measurement)
                // Upload the measurement to the server
                uploadMeasurement(measurement)
            }

            Log.d("SignalMeasurement", "Data stored and uploaded: $measurement")
        } else {
            Log.e("SignalMeasurement", "Failed to retrieve location or signal strength")
        }

        scheduleNextMeasurement()
    }


    private fun getLastKnownLocation(): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
        for (provider in providers) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.getLastKnownLocation(provider)?.let {
                    return it
                }
            }
        }
        return null
    }

    private fun uploadMeasurement(measurement: SignalMeasurement) {
        api.uploadMeasurement(measurement).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("SignalMeasurement", "Data uploaded successfully")
                } else {
                    Log.e("SignalMeasurement", "Upload failed: ${response.code()}")
                }
            }

            override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {
                Log.e("SignalMeasurement", "Error: ${t.message}")
            }
        })
    }

    private fun scheduleNextMeasurement() {
        // Implement scheduling logic here (e.g., using Handler or AlarmManager)
    }
}
