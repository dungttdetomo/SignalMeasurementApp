package com.example.signalmeasurementapp.data

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface SignalMeasurementApi {

    @POST("measurements")
    fun uploadMeasurement(@Body measurement: SignalMeasurement): Call<Void>
}
