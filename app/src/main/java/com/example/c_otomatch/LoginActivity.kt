package com.example.c_otomatch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.c_otomatch.databinding.ActivityLoginBinding
// HAPUS Prefs
// import com.example.c_otomatch.utils.Prefs
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth // IMPORT AUTH

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        binding.btnLogin.setOnClickListener {
            loginUser()
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser() {
        val emailOrName = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (emailOrName.isEmpty()) {
            binding.etEmail.error = "Masukkan email"
            return
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Masukkan password"
            return
        }

        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "Logging in..."

        // Login dengan Firebase Auth
        auth.signInWithEmailAndPassword(emailOrName, password) // Asumsi user login pakai email
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "signInWithEmail:success")
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Log.w("LoginActivity", "signInWithEmail:failure", task.exception)
                    Snackbar.make(binding.root, "Login gagal: Email atau password salah.", Snackbar.LENGTH_LONG).show()
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "Log In"
                }
            }
    }
}