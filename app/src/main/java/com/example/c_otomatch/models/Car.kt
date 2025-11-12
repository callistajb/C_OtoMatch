package com.example.c_otomatch.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Ini model data mobil, disamain sama semua field di form Jual & Edit
data class Car(
    @get:Exclude
    var documentId: String = "",

    // Info Utama
    var name: String = "",
    var brand: String = "",
    var model: String = "", // <-- PENTING
    var year: Int = 2000,
    var price: String = "",
    var mileage: String = "",
    var location: String = "",
    var imageUrl: String = "",
    var isSold: Boolean = false,

    // Info Penjual
    var sellerName: String = "",
    var sellerContact: String = "",
    var sellerType: String = "", // <-- PENTING
    var sellerUid: String = "",

    // Info Spek
    var bodyType: String = "",
    var color: String = "",
    var transmission: String = "",
    var fuel: String = "",
    var capacity: String = "", // <-- PENTING (Kapasitas Mesin)
    var variant: String = "",  // <-- PENTING

    // Info Tambahan
    var negatives: String = "", // <-- PENTING (Minus)
    var mods: String = "",      // <-- PENTING (Modifikasi)

    // Lain-lain
    @ServerTimestamp // Otomatis diisi Firebase pas dibuat
    val createdAt: Date? = null, // <-- PENTING, buat sorting 'Terbaru'
    var isWishlist: Boolean = false,

    // Ini data lama, biarin aja
    var id: Int = 0,
    var kmRange: String = ""
)