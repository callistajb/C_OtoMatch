package com.example.c_otomatch

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.c_otomatch.databinding.ActivityLoginBinding
import com.example.c_otomatch.utils.Prefs
import com.google.android.material.snackbar.Snackbar

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cek: kalau user sudah login, langsung ke home
        if (Prefs.isLoggedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        binding.btnLogin.setOnClickListener {
            val emailOrName = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (emailOrName.isEmpty()) {
                binding.etEmail.error = "Masukkan email atau nama"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                binding.etPassword.error = "Masukkan password"
                return@setOnClickListener
            }

            val savedName = Prefs.getName(this)
            val savedEmail = Prefs.getEmail(this)

            // Jika user belum pernah register
            if (savedEmail.isEmpty() && savedName.isEmpty()) {
                Snackbar.make(binding.root, "Akun belum terdaftar. Silakan daftar terlebih dahulu.", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Kalau cocok (bisa pakai nama atau email)
            if (emailOrName == savedEmail || emailOrName == savedName) {
                Prefs.setLoggedIn(this, savedName, savedEmail)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                Snackbar.make(binding.root, "Data tidak cocok. Coba lagi.", Snackbar.LENGTH_SHORT).show()
            }
        }

        // Kalau user klik teks “Belum punya akun? Daftar”
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
