package com.example.c_otomatch

import android.app.Application
import com.cloudinary.android.MediaManager

// Ini kelas "Jantung" aplikasi kita.
// Dia jalan cuma SEKALI pas aplikasi pertama kali dibuka.
class MyApplication : Application() {

    // Masukin Cloud Name kamu disini ya
    private val CLOUD_NAME = "dqehqqz7q"

    override fun onCreate() {
        super.onCreate()

        // Nah, init Cloudinary-nya DISINI AJA.
        // Jangan di Activity atau Fragment lain biar ga crash "Already Initialized".
        try {
            val config = hashMapOf(
                "cloud_name" to CLOUD_NAME
            )
            MediaManager.init(this, config)
        } catch (e: Exception) {
            // Kalo udah pernah init, ya udah lanjut aja.
        }
    }
}