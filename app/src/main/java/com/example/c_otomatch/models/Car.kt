package com.example.c_otomatch.models

data class Car(
    val id: Int,
    val name: String,
    val brand: String,
    val year: Int,
    val price: String,
    val mileage: String,
    val location: String,
    val imageResId: Int,
    var isFavorite: Boolean
)