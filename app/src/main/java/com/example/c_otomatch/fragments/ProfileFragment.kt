package com.example.c_otomatch.fragments

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.c_otomatch.LoginActivity
import com.example.c_otomatch.R
import com.example.c_otomatch.utils.Prefs

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
    private lateinit var btnViewReviews: Button

    private var currentPhotoUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_profile, container, false)

        ivProfile = v.findViewById(R.id.ivProfile)
        tvName = v.findViewById(R.id.tvName)
        tvUsername = v.findViewById(R.id.tvUsername)
        tvPhone = v.findViewById(R.id.tvPhone)
        tvLocation = v.findViewById(R.id.tvLocation)
        btnEditProfile = v.findViewById(R.id.btnEditProfile)
        btnLogout = v.findViewById(R.id.btnLogout)
        ratingBar = v.findViewById(R.id.ratingBar)
        tvRatingValue = v.findViewById(R.id.tvRatingValue)
        btnViewReviews = v.findViewById(R.id.btnViewReviews)

        loadProfile()

        btnEditProfile.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, EditProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        btnLogout.setOnClickListener { showLogoutConfirmation() }

        btnViewReviews.setOnClickListener {
            Toast.makeText(requireContext(), "Halaman review belum diimplementasi", Toast.LENGTH_SHORT).show()
        }

        return v
    }

    private fun loadProfile() {
        tvName.text = Prefs.getName(requireContext())
        tvUsername.text = Prefs.getEmail(requireContext())
        tvPhone.text = Prefs.getPhone(requireContext())
        tvLocation.text = Prefs.getLocation(requireContext())

        val rating = Prefs.getRating(requireContext())
        ratingBar.rating = rating.toFloat()
        tvRatingValue.text = String.format("%.1f ★", rating)

        Prefs.getProfileImageUri(requireContext())?.let {
            try {
                val uri = Uri.parse(it)
                currentPhotoUri = uri
                ivProfile.setImageURI(uri)
            } catch (_: Exception) {}
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Konfirmasi Logout")
            .setMessage("Apakah Anda yakin ingin keluar dari akun ini?")
            .setPositiveButton("Ya") { _, _ ->
                Prefs.logout(requireContext())
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
