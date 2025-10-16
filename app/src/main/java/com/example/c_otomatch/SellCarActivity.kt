package com.example.c_otomatch

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.c_otomatch.databinding.ActivityAddCarBinding
import com.example.c_otomatch.models.Car
import java.io.ByteArrayOutputStream

class SellCarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddCarBinding
    private var imageUri: Uri? = null
    private var imageResIdFallback = R.drawable.ic_car

    //Ambil dari gallery
    private val pickGallery =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                imageUri = uri
                binding.imgPreview.setImageURI(uri)
            }
        }

    // Ambil langsung dari camera
    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) {
                binding.imgPreview.setImageBitmap(bitmap)
                // Optional: convert to Uri if you want to pass back — here we'll just set preview
                // and not save to storage (gimmick aja)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Jual Mobil"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val toolbar = findViewById<Toolbar>(R.id.toolbarSellCar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        
        binding.btnGallery.setOnClickListener {
            pickGallery.launch("image/*")
        }

        binding.btnCamera.setOnClickListener {
            // gimmick camera: TakePicturePreview (no storage)
            takePhoto.launch(null)
        }

        binding.btnGeneratePrice.setOnClickListener {
            val saran = generatePriceSuggestion()
            binding.tvGeneratedPrice.text = "Harga saran: $saran"
            binding.etPrice.setText(saran)
        }

        binding.btnSubmit.setOnClickListener {
            if (validateInputs()) {
                AlertDialog.Builder(this)
                    .setTitle("Konfirmasi Posting")
                    .setMessage("Yakin ingin memposting mobil ini untuk dijual?")
                    .setPositiveButton("Ya") { _, _ ->
                        submitCar()
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun validateInputs(): Boolean {
        if (binding.etBrand.text.isNullOrBlank()) {
            binding.etBrand.error = "Merek wajib diisi"
            return false
        }
        if (binding.etModel.text.isNullOrBlank()) {
            binding.etModel.error = "Tipe/model wajib diisi"
            return false
        }
        if (binding.etYear.text.isNullOrBlank()) {
            binding.etYear.error = "Tahun wajib diisi"
            return false
        }
        if (binding.etColor.text.isNullOrBlank()) {
            binding.etColor.error = "Warna wajib diisi"
            return false
        }
        if (binding.etMileage.text.isNullOrBlank()) {
            binding.etMileage.error = "Kilometer wajib diisi"
            return false
        }
        if (binding.etLocation.text.isNullOrBlank()) {
            binding.etLocation.error = "Lokasi wajib diisi"
            return false
        }
        return true
    }

    // Generate harga
    private fun generatePriceSuggestion(): String {
        val year = binding.etYear.text.toString().toIntOrNull() ?: 2020
        val mileageText = binding.etMileage.text.toString().replace("[^0-9]".toRegex(), "")
        val mileage = mileageText.toIntOrNull() ?: 0

        // heuristic: base price (jutaan)
        val base = when {
            year >= 2023 -> 350_000_000
            year >= 2019 -> 230_000_000
            year >= 2015 -> 150_000_000
            else -> 80_000_000
        }

        // degrade by mileage
        val discount = (mileage / 20_000) * 5_000_000 // tiap 20k km kurangi 5 juta
        val suggested = (base - discount).coerceAtLeast(20_000_000)
        return "Rp %,d".format(suggested)
    }

    private fun submitCar() {
        // Build Car object — we will return minimal required fields via Intent extras
        val id = (intent.getIntExtra("next_id", 0)).takeIf { it > 0 } ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val name = "${binding.etBrand.text} ${binding.etModel.text}"
        val brand = binding.etBrand.text.toString()
        val year = binding.etYear.text.toString().toIntOrNull() ?: 2020
        val priceText = if (binding.etPrice.text.isNullOrBlank()) binding.tvGeneratedPrice.text.toString().replace("Harga saran: ", "") else binding.etPrice.text.toString()
        val mileage = binding.etMileage.text.toString()
        val location = binding.etLocation.text.toString()

        // For simplicity we pass back a resource id if we used fallback placeholder; if user picked an image uri
        // we return URI string if available; SellFragment can decide how to use it.
        val result = Intent().apply {
            putExtra("id", id)
            putExtra("name", name)
            putExtra("brand", brand)
            putExtra("year", year)
            putExtra("price", priceText)
            putExtra("mileage", mileage)
            putExtra("location", location)
            putExtra("isWishlist", false)
            putExtra("isSold", false)
            imageUri?.let { putExtra("image_uri", it.toString()) } ?: putExtra("image_res", imageResIdFallback)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
