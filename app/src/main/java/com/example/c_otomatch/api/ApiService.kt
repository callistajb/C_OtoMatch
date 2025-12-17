package com.example.c_otomatch.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("predict")
    fun predictPrice(@Body request: PredictionRequest): Call<PredictionResponse>
}