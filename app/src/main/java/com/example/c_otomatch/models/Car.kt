package com.example.c_otomatch.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Car(
    @get:Exclude
    var documentId: String = "",

    var name: String = "",
    var brand: String = "",
    var model: String = "",
    var year: Int = 2000,
    var price: String = "0",
    var mileage: String = "0 km",
    var location: String = "",

    @get:PropertyName("imageUrl") @set:PropertyName("imageUrl")
    var imageUrl: String = "",

    @get:PropertyName("imageUrls") @set:PropertyName("imageUrls")
    var imageUrls: List<String> = ArrayList(),

    @get:PropertyName("isSold") @set:PropertyName("isSold")
    var isSold: Boolean = false,

    @get:Exclude
    var isWishlist: Boolean = false,

    var sellerName: String = "",
    var sellerContact: String = "",
    var sellerType: String = "",
    var sellerUid: String = "",

    var bodyType: String = "",

    // Kategori Warna (misal: "Merah") - Untuk Filter
    var color: String = "",

    // Nama Warna Unik (misal: "Soul Red Crystal") - Untuk Tampilan
    var exactColor: String = "",

    var transmission: String = "",
    var fuel: String = "",
    var capacity: String = "",
    var variant: String = "",
    var negatives: String = "",
    var mods: String = "",
    var kmRange: String = "",

    @ServerTimestamp
    val createdAt: Date? = null,
    var id: Int = 0
)