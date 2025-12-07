package com.example.c_otomatch

import android.app.Application
import com.cloudinary.android.MediaManager

class MyApplication : Application() {

    private val CLOUD_NAME = "dqehqqz7q"

    override fun onCreate() {
        super.onCreate()

        try {
            val config = hashMapOf(
                "cloud_name" to CLOUD_NAME
            )
            MediaManager.init(this, config)
        } catch (e: Exception) {
        }
    }
}