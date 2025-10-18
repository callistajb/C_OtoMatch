package com.example.c_otomatch.models

data class Car(
    val id: Int,
    var name: String,
    var brand: String,
    var year: Int,
    var price: String,
    var mileage: String,
    var location: String,
    var imageResId: Int,
    var isWishlist: Boolean,
    var isSold: Boolean = false,
    var sellerName: String,
    var sellerContact: String,
    var bodyType: String = "",
    var color: String = "",
    var transmission: String = "",
    var fuel: String = "",
    var kmRange: String = ""
)

