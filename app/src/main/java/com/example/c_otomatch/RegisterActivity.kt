package com.example.c_otomatch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns // Import buat validasi email
import androidx.appcompat.app.AppCompatActivity
import com.example.c_otomatch.databinding.ActivityRegisterBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.btnRegister.setOnClickListener {
            registerUser()
        }

        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {
        // Ambil semua data baru
        val name = binding.inputName.text.toString().trim()
        val username = binding.inputUsername.text.toString().trim()
        val email = binding.inputEmail.text.toString().trim()
        val phone = binding.inputPhone.text.toString().trim()
        val password = binding.inputPassword.text.toString()
        val confirmPassword = binding.inputConfirmPassword.text.toString()

        // Validasi, biar datanya bener
        if (name.isEmpty()) {
            binding.inputName.error = "Nama lengkap wajib diisi"
            binding.inputName.requestFocus()
            return
        }
        if (username.length < 4) {
            binding.inputUsername.error = "Username minimal 4 karakter"
            binding.inputUsername.requestFocus()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.inputEmail.error = "Email tidak valid"
            binding.inputEmail.requestFocus()
            return
        }
        if (phone.length < 10) { // Anggep aja no HP minimal 10 digit
            binding.inputPhone.error = "Nomor HP tidak valid"
            binding.inputPhone.requestFocus()
            return
        }
        if (password.length < 6) {
            binding.inputPassword.error = "Password minimal 6 karakter"
            binding.inputPassword.requestFocus()
            return
        }
        if (confirmPassword != password) {
            binding.inputConfirmPassword.error = "Password tidak cocok"
            binding.inputConfirmPassword.requestFocus()
            return
        }

        // Kalo semua validasi lolos, baru gaskeun
        binding.btnRegister.isEnabled = false
        binding.btnRegister.text = "Mendaftarkan..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("RegisterActivity", "createUserWithEmail:success")
                    val firebaseUser = auth.currentUser
                    val uid = firebaseUser?.uid

                    if (uid == null) {
                        // Ini jaga-jaga aja, harusnya ga mungkin null
                        Snackbar.make(binding.root, "Registrasi gagal: UID tidak ditemukan", Snackbar.LENGTH_LONG).show()
                        binding.btnRegister.isEnabled = true
                        binding.btnRegister.text = "Daftar"
                        return@addOnCompleteListener
                    }

                    // Update nama di profil Auth (biar muncul di 'Hi, Nama')
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    firebaseUser.updateProfile(profileUpdates)

                    // Simpen data lengkap user ke Firestore
                    createUserDocumentInFirestore(uid, name, username, email, phone)

                } else {
                    // Kalo gagal bikin user (misal email udah ada)
                    Log.w("RegisterActivity", "createUserWithEmail:failure", task.exception)
                    Snackbar.make(binding.root, "Registrasi gagal: ${task.exception?.message}", Snackbar.LENGTH_LONG).show()
                    binding.btnRegister.isEnabled = true
                    binding.btnRegister.text = "Daftar"
                }
            }
    }

    private fun createUserDocumentInFirestore(uid: String, name: String, username: String, email: String, phone: String) {
        val userData = hashMapOf(
            "uid" to uid,
            "name" to name,
            "username" to username,
            "email" to email,
            "phone" to phone,
            "location" to "",
            "profileImageUrl" to "",
            "rating" to 0.0, // <--- INI SUDAH DIPERBAIKI JADI 0.0
            "wishlist" to emptyList<String>()
        )

        db.collection("users").document(uid)
            .set(userData)
            .addOnSuccessListener {
                Log.d("RegisterActivity", "User document created in Firestore")
                Snackbar.make(binding.root, "Registrasi berhasil — Selamat datang, $name!", Snackbar.LENGTH_SHORT).show()

                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.w("RegisterActivity", "Error creating user document", e)
                Snackbar.make(binding.root, "Registrasi gagal (db): ${e.message}", Snackbar.LENGTH_LONG).show()
                binding.btnRegister.isEnabled = true
                binding.btnRegister.text = "Daftar"
                auth.currentUser?.delete()
            }
    }
}