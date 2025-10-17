package com.example.c_otomatch

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.c_otomatch.databinding.ActivitySplashBinding
import com.example.c_otomatch.utils.Prefs

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // play a small fade/scale animation on logo (use view animation resources)
        binding.logoImage.startAnimation(
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in)
        )

        // delay then navigate
        Handler(Looper.getMainLooper()).postDelayed({
            val target = if (Prefs.isLoggedIn(this)) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            startActivity(target)
            overridePendingTransition(R.anim.slide_up, R.anim.slide_out_up)
            finish()
        }, 1200) // 1.2s splash
    }
}
