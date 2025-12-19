package com.example.c_otomatch

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.c_otomatch.databinding.ActivitySplashBinding
import com.example.c_otomatch.utils.Data // Import Data
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        //Data.uploadDataToFirebase()

        binding.logoImage.startAnimation(
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in)
        )

        Handler(Looper.getMainLooper()).postDelayed({
            val target: Intent
            if (auth.currentUser != null) {
                target = Intent(this, MainActivity::class.java)
            } else {
                target = Intent(this, LoginActivity::class.java)
            }

            startActivity(target)
            overridePendingTransition(R.anim.slide_up, R.anim.slide_out_up)
            finish()
        }, 1200)
    }
}