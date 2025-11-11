package com.example.c_otomatch.models

import com.google.firebase.firestore.Exclude

data class Car(
    @get:Exclude
    var documentId: String = "",

    val id: Int = 0,
    var name: String = "",
    var brand: String = "",
    var year: Int = 2000,
    var price: String = "",
    var mileage: String = "",
    var location: String = "",
    var imageUrl: String = "", // GANTI DARI imageResId
    var isWishlist: Boolean = false,
    var isSold: Boolean = false,
    var sellerName: String = "",
    var sellerContact: String = "",
    var bodyType: String = "",
    var color: String = "",
    var transmission: String = "",
    var fuel: String = "",
    var kmRange: String = "",
    var sellerUid: String = ""
)