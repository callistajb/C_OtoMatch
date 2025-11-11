package com.example.c_otomatch.fragments

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide // IMPORT GLIDE
import com.example.c_otomatch.LoginActivity
import com.example.c_otomatch.R
// HAPUS Prefs
// import com.example.c_otomatch.utils.Prefs
import com.google.firebase.auth.FirebaseAuth // IMPORT AUTH
import com.google.firebase.firestore.FirebaseFirestore // IMPORT FIRESTORE

class ProfileFragment : Fragment() {

    private lateinit var ivProfile: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvUsername: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvLocation: TextView
    private lateinit var btnEditProfile: Button
    private lateinit var btnLogout: Button
    private lateinit var ratingBar: RatingBar
    private lateinit var tvRatingValue: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_profile, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        ivProfile = v.findViewById(R.id.ivProfile)
        tvName = v.findViewById(R.id.tvName)
        tvUsername = v.findViewById(R.id.tvUsername)
        tvPhone = v.findViewById(R.id.tvPhone)
        tvLocation = v.findViewById(R.id.tvLocation)
        btnEditProfile = v.findViewById(R.id.btnEditProfile)
        btnLogout = v.findViewById(R.id.btnLogout)
        ratingBar = v.findViewById(R.id.ratingBar)
        tvRatingValue = v.findViewById(R.id.tvRatingValue)

        loadProfile()

        btnEditProfile.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, EditProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        btnLogout.setOnClickListener { showLogoutConfirmation() }

        return v
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun loadProfile() {
        val user = auth.currentUser
        if (user == null) {
            goToLogin()
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    tvName.text = document.getString("name") ?: "Nama Belum Diatur"
                    tvUsername.text = document.getString("email") ?: user.email
                    tvPhone.text = document.getString("phone") ?: "No. HP Belum Diatur"
                    tvLocation.text = document.getString("location") ?: "Lokasi Belum Diatur"

                    val rating = document.getDouble("rating")?.toFloat() ?: 4.7f
                    ratingBar.rating = rating
                    tvRatingValue.text = String.format("%.1f ★", rating)

                    val imageUrl = document.getString("profileImageUrl")
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_person)
                            .circleCrop()
                            .into(ivProfile)
                    } else {
                        ivProfile.setImageResource(R.drawable.ic_person)
                    }

                } else {
                    Log.w("ProfileFragment", "No such document")
                }
            }
            .addOnFailureListener { exception ->
                Log.w("ProfileFragment", "get failed with ", exception)
                Toast.makeText(context, "Gagal memuat profil", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Konfirmasi Logout")
            .setMessage("Apakah Anda yakin ingin keluar dari akun ini?")
            .setPositiveButton("Ya") { _, _ ->
                // Logout dari Firebase Auth
                auth.signOut()

                goToLogin()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun goToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}