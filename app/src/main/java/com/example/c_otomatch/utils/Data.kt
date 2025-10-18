package com.example.c_otomatch.utils

import com.example.c_otomatch.R
import com.example.c_otomatch.models.Car

object Data {
    val carList = listOf(
        Car(
            id = 1,
            name = "Civic Turbo",
            brand = "Honda",
            year = 2021,
            price = "Rp 420.000.000",
            mileage = "20.000 km",
            location = "Tangerang",
            imageResId = R.drawable.civic,
            isWishlist = false,
            isSold = false,
            sellerName = "Callista Jasmine",
            sellerContact = "081234567890",
            bodyType = "Sedan",
            color = "Hitam",
            transmission = "Automatic",
            fuel = "Bensin",
            kmRange = "<50.000 km"
        ),
        Car(
            id = 2,
            name = "Fortuner VRZ",
            brand = "Toyota",
            year = 2020,
            price = "Rp 520.000.000",
            mileage = "35.000 km",
            location = "Jakarta",
            imageResId = R.drawable.fortuner,
            isWishlist = false,
            isSold = false,
            sellerName = "Callista Jasmine",
            sellerContact = "081234567890",
            bodyType = "SUV",
            color = "Putih",
            transmission = "Automatic",
            fuel = "Diesel",
            kmRange = "50.000-100.000 km"
        ),
        Car(
            id = 3,
            name = "Xpander Ultimate",
            brand = "Mitsubishi",
            year = 2019,
            price = "Rp 250.000.000",
            mileage = "40.000 km",
            location = "Bekasi",
            imageResId = R.drawable.xpander,
            isWishlist = false,
            isSold = false,
            sellerName = "Budi Santoso",
            sellerContact = "082233445566",
            bodyType = "MPV",
            color = "Silver",
            transmission = "Manual",
            fuel = "Bensin",
            kmRange = "50.000-100.000 km"
        ),
        Car(
            id = 4,
            name = "Mazda 3",
            brand = "Mazda",
            year = 2022,
            price = "Rp 480.000.000",
            mileage = "10.000 km",
            location = "BSD City",
            imageResId = R.drawable.mazda3,
            isWishlist = false,
            isSold = false,
            sellerName = "Andi Wijaya",
            sellerContact = "083344556677",
            bodyType = "Sedan",
            color = "Biru",
            transmission = "Automatic",
            fuel = "Bensin",
            kmRange = "<50.000 km"
        )
    )
}
