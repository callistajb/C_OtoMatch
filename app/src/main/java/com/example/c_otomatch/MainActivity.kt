package com.example.c_otomatch

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.example.c_otomatch.fragments.*
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔹 Setup Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // 🔹 Setup Bottom Nav
        bottomNav = findViewById(R.id.bottomNavigation)
        loadFragment(HomeFragment())

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_wishlist -> loadFragment(WishlistFragment())
                R.id.nav_sell -> loadFragment(SellFragment())
                R.id.nav_profile -> loadFragment(ProfileFragment())
            }
            true
        }

        bottomNav.setOnItemReselectedListener { /* do nothing */ }
    }

    private fun loadFragment(fragment: Fragment) {
        // 🔹 Title Otomatis
        supportActionBar?.title = when (fragment) {
            is HomeFragment -> "Home"
            is WishlistFragment -> "Wishlist"
            is SellFragment -> "Jual Mobil"
            is ProfileFragment -> "Profile"
            else -> "OtoMatch"
        }

        // 🔹 Transisi Animasi
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
