package com.example.c_otomatch

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import android.view.View
import com.example.c_otomatch.fragments.*
import com.example.c_otomatch.utils.Prefs
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var imgLogo: ImageView
    private lateinit var tvGreeting: TextView
    private lateinit var etSearch: EditText
    private lateinit var imgSearchIcon: ImageView
    private lateinit var searchBarLayout: View
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNavigation)
        imgLogo = findViewById(R.id.imgLogo)
        tvGreeting = findViewById(R.id.tvGreeting)
        etSearch = findViewById(R.id.etSearch)
        imgSearchIcon = findViewById(R.id.imgSearchIcon)
        searchBarLayout = findViewById(R.id.searchBarLayout)

        // Greeting dari SharedPreferences
        val userName = Prefs.getName(this)
        tvGreeting.text = if (!userName.isNullOrEmpty()) "Hi, $userName" else "Hi, Selamat datang!"

        // Klik logo → kembali ke Home
        imgLogo.setOnClickListener {
            bottomNav.selectedItemId = R.id.nav_home
            loadFragment(HomeFragment())
        }

        // Load default fragment
        loadFragment(HomeFragment())

        // Bottom Navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_wishlist -> loadFragment(WishlistFragment())
                R.id.nav_sell -> loadFragment(SellFragment())
                R.id.nav_profile -> loadFragment(ProfileFragment())
            }
            true
        }

        bottomNav.setOnItemReselectedListener { }

        // Fitur pencarian
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (currentFragment is HomeFragment) {
                    (currentFragment as HomeFragment).filterCars(query)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadFragment(fragment: Fragment) {
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        if (fragment is ProfileFragment) {
            etSearch.visibility = View.GONE
            imgSearchIcon.visibility = View.GONE
            searchBarLayout.visibility = View.GONE
        } else {
            etSearch.visibility = View.VISIBLE
            imgSearchIcon.visibility = View.VISIBLE
            searchBarLayout.visibility = View.VISIBLE
        }
    }
}
