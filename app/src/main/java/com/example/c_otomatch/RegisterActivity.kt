package com.example.c_otomatch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.c_otomatch.databinding.ActivityRegisterBinding
// HAPUS Prefs
// import com.example.c_otomatch.utils.Prefs
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth // IMPORT AUTH
import com.google.firebase.auth.UserProfileChangeRequest // IMPORT UNTUK UPDATE NAMA
import com.google.firebase.firestore.FirebaseFirestore // IMPORT FIRESTORE

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
        val name = binding.inputName.text.toString().trim()
        val email = binding.inputEmail.text.toString().trim()
        val password = binding.inputPassword.text.toString()
        val confirmPassword = binding.inputConfirmPassword.text.toString()

        when {
            name.isEmpty() -> {
                binding.inputName.error = "Masukkan nama lengkap"
                return
            }
            email.isEmpty() -> {
                binding.inputEmail.error = "Masukkan email"
                return
            }
            password.length < 6 -> {
                binding.inputPassword.error = "Password minimal 6 karakter"
                return
            }
            confirmPassword != password -> {
                binding.inputConfirmPassword.error = "Password tidak cocok"
                return
            }
            else -> {
                binding.btnRegister.isEnabled = false
                binding.btnRegister.text = "Mendaftarkan..."

                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("RegisterActivity", "createUserWithEmail:success")
                            val firebaseUser = auth.currentUser

                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .setDisplayName(name)
                                .build()

                            firebaseUser?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                createUserDocumentInFirestore(firebaseUser.uid, name, email)
                            }

                        } else {
                            Log.w("RegisterActivity", "createUserWithEmail:failure", task.exception)
                            Snackbar.make(binding.root, "Registrasi gagal: ${task.exception?.message}", Snackbar.LENGTH_LONG).show()
                            binding.btnRegister.isEnabled = true
                            binding.btnRegister.text = "Daftar"
                        }
                    }
            }
        }
    }

    private fun createUserDocumentInFirestore(uid: String, name: String, email: String) {
        val userData = hashMapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "phone" to "",
            "location" to "",
            "profileImageUrl" to "",
            "rating" to 4.7f
        )

        // Simpan ke collection "users" dengan ID dokumen = UID user
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
            }
    }
}