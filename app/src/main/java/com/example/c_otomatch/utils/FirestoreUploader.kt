package com.example.c_otomatch.utils

import android.util.Log
import com.example.c_otomatch.models.Car
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreUploader {
    private val db = FirebaseFirestore.getInstance()

    fun uploadCarsToFirestore(cars: List<Car>) {
        val collectionRef = db.collection("cars")

        for (car in cars) {
            // Data ini sekarang LENGKAP dan BENAR
            val carData = hashMapOf(
                "id" to car.id,
                "name" to car.name,
                "brand" to car.brand,
                "year" to car.year,
                "price" to car.price,
                "mileage" to car.mileage,
                "location" to car.location,
                "imageUrl" to car.imageUrl, // <-- DIPERBAIKI
                "sellerName" to car.sellerName,
                "sellerContact" to car.sellerContact,
                "bodyType" to car.bodyType,
                "color" to car.color,
                "transmission" to car.transmission,
                "fuel" to car.fuel,
                "kmRange" to car.kmRange,
                "isWishlist" to car.isWishlist,
                "isSold" to car.isSold,
                "sellerUid" to car.sellerUid // <-- DIPERBAIKI
            )

            collectionRef.document("car_seed_${car.id}")
                .set(carData)
                .addOnSuccessListener {
                    Log.d("FirestoreUploader", "✅ Berhasil upload ${car.name}")
                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreUploader", "❌ Gagal upload ${car.name}", e)
                }
        }
    }
}