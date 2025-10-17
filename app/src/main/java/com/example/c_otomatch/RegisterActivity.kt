package com.example.c_otomatch

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.c_otomatch.databinding.ActivityRegisterBinding
import com.example.c_otomatch.utils.Prefs
import com.google.android.material.snackbar.Snackbar

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener {
            val name = binding.inputName.text.toString().trim()
            val email = binding.inputEmail.text.toString().trim()
            val password = binding.inputPassword.text.toString()
            val confirmPassword = binding.inputConfirmPassword.text.toString()

            // Validasi
            when {
                name.isEmpty() -> {
                    binding.inputName.error = "Masukkan nama lengkap"
                    return@setOnClickListener
                }
                email.isEmpty() -> {
                    binding.inputEmail.error = "Masukkan email"
                    return@setOnClickListener
                }
                password.length < 4 -> {
                    binding.inputPassword.error = "Password minimal 4 karakter"
                    return@setOnClickListener
                }
                confirmPassword != password -> {
                    binding.inputConfirmPassword.error = "Password tidak cocok"
                    return@setOnClickListener
                }
                else -> {
                    // Simpan ke SharedPreferences
                    Prefs.setLoggedIn(this, name, email)
                    Snackbar.make(binding.root, "Registrasi berhasil — Selamat datang, $name!", Snackbar.LENGTH_SHORT).show()

                    // Pindah ke Home
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            }
        }

        // TextView "Already have an account? Log in"
        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
