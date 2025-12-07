package com.example.c_otomatch.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Car(
    @get:Exclude
    var documentId: String = "",

    var name: String = "",
    var brand: String = "",
    var model: String = "",
    var year: Int = 2000,
    var price: String = "",
    var mileage: String = "",
    var location: String = "",

    // --- UPDATE DI SINI ---
    var imageUrl: String = "", // Foto utama (Thumbnail)
    var imageUrls: List<String> = emptyList(), // List semua foto untuk slider
    // ----------------------

    var isSold: Boolean = false,
    var sellerName: String = "",
    var sellerContact: String = "",
    var sellerType: String = "",
    var sellerUid: String = "",

    var bodyType: String = "",
    var color: String = "",
    var transmission: String = "",
    var fuel: String = "",
    var capacity: String = "",
    var variant: String = "",
    var negatives: String = "",
    var mods: String = "",

    @ServerTimestamp
    val createdAt: Date? = null,
    var isWishlist: Boolean = false,
    var id: Int = 0,
    var kmRange: String = ""
)