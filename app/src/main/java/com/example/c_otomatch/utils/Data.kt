package com.example.c_otomatch.utils

import com.example.c_otomatch.R
import com.example.c_otomatch.models.Car

object Data {
    val carList = listOf(
        Car(1, "Civic Turbo", "Honda", 2021, "Rp 420.000.000", "20.000 km", "Tangerang", R.drawable.civic, false, bodyType="Sedan", color="Hitam", transmission="Automatic", fuel="Bensin", kmRange="<50.000 km"),
        Car(2, "Fortuner VRZ", "Toyota", 2020, "Rp 520.000.000", "35.000 km", "Jakarta", R.drawable.fortuner, false, bodyType="SUV", color="Putih", transmission="Automatic", fuel="Diesel", kmRange="50.000-100.000 km"),
        Car(3, "Xpander Ultimate", "Mitsubishi", 2019, "Rp 250.000.000", "40.000 km", "Bekasi", R.drawable.xpander, false, bodyType="MPV", color="Silver", transmission="Manual", fuel="Bensin", kmRange="50.000-100.000 km"),
        Car(4, "Mazda 3", "Mazda", 2022, "Rp 480.000.000", "10.000 km", "BSD City", R.drawable.mazda3, false, bodyType="Sedan", color="Biru", transmission="Automatic", fuel="Bensin", kmRange="<50.000 km")
    )
}