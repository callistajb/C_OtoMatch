package com.example.c_otomatch.api

import com.google.gson.annotations.SerializedName

data class PredictionRequest(
    @SerializedName("brand") val brand: String,
    @SerializedName("model") val model: String,
    @SerializedName("year") val year: Int,
    @SerializedName("transmission") val transmission: String,
    @SerializedName("fuel") val fuel: String,
    @SerializedName("mileage") val mileage: Int,
    @SerializedName("capacity") val capacity: Int
)

data class PredictionResponse(
    @SerializedName("status") val status: String,
    @SerializedName("predicted_price") val predictedPrice: Double,
    @SerializedName("formatted_price") val formattedPrice: String,
    @SerializedName("corrected_brand") val correctedBrand: String? = null,
    @SerializedName("corrected_model") val correctedModel: String? = null,
    @SerializedName("message") val message: String? = null
)