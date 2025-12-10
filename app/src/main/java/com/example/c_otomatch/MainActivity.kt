package com.example.c_otomatch

import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import android.util.Log
import android.os.Bundle
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import android.view.View
import com.example.c_otomatch.fragments.*
import com.example.c_otomatch.utils.Data
import com.example.c_otomatch.utils.FirestoreUploader
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var imgLogo: ImageView
    private lateinit var tvGreeting: TextView
    private lateinit var etSearch: EditText
    private lateinit var imgSearchIcon: ImageView
    private lateinit var btnMatchmaker: ImageButton // Variabel Baru
    private lateinit var searchBarLayout: View
    private var currentFragment: Fragment? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        bottomNav = findViewById(R.id.bottomNavigation)
        imgLogo = findViewById(R.id.imgLogo)
        tvGreeting = findViewById(R.id.tvGreeting)
        etSearch = findViewById(R.id.etSearch)
        imgSearchIcon = findViewById(R.id.imgSearchIcon)
        searchBarLayout = findViewById(R.id.searchBarLayout)
        btnMatchmaker = findViewById(R.id.btnMatchmakerMain) // Inisialisasi Tombol Baru

        // ... (Logika Greeting user tetap sama) ...
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            db.collection("users").document(firebaseUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val userName = document.getString("name")
                        tvGreeting.text = if (!userName.isNullOrEmpty()) "Hi, $userName" else "Hi, User"
                    } else {
                        tvGreeting.text = "Hi, ${firebaseUser.email?.split('@')?.get(0) ?: "User"}"
                    }
                }
                .addOnFailureListener {
                    tvGreeting.text = "Hi, Selamat datang!"
                }
        } else {
            tvGreeting.text = "Hi, Selamat datang!"
        }

        imgLogo.setOnClickListener {
            bottomNav.selectedItemId = R.id.nav_home
            loadFragment(HomeFragment())
        }

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

        bottomNav.setOnItemReselectedListener { }

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

        // --- LISTENER TOMBOL FILTER DI PINDAH KESINI ---
        btnMatchmaker.setOnClickListener {
            showMatchmakerDialog()
        }
    }

    // --- FUNGSI DIALOG DIPINDAHKAN KE SINI ---
    private fun showMatchmakerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_matchmaker, null)
        val actBudget = dialogView.findViewById<AutoCompleteTextView>(R.id.actBudget)
        val actType = dialogView.findViewById<AutoCompleteTextView>(R.id.actType)
        val btnFind = dialogView.findViewById<Button>(R.id.btnFindMatch)

        val budgets = listOf("Di bawah 200 Juta", "200 - 500 Juta", "Di atas 500 Juta", "Tampilkan Semua")
        val types = listOf("SUV", "Sedan", "MPV", "Hatchback", "Tampilkan Semua")

        actBudget.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, budgets))
        actType.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, types))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnFind.setOnClickListener {
            val selectedBudget = actBudget.text.toString()
            val selectedType = actType.text.toString()

            val intent = Intent(this, MatchActivity::class.java)
            intent.putExtra("FILTER_BUDGET", selectedBudget)
            intent.putExtra("FILTER_TYPE", selectedType)
            startActivity(intent)

            dialog.dismiss()
        }

        dialog.show()
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
            btnMatchmaker.visibility = View.GONE // Sembunyikan tombol filter di profile
        } else {
            etSearch.visibility = View.VISIBLE
            imgSearchIcon.visibility = View.VISIBLE
            searchBarLayout.visibility = View.VISIBLE
            // Tampilkan tombol filter HANYA di HomeFragment
            btnMatchmaker.visibility = if (fragment is HomeFragment) View.VISIBLE else View.GONE
        }
    }
}