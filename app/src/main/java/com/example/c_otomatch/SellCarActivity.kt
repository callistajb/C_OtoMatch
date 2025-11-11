package com.example.c_otomatch

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.c_otomatch.databinding.ActivityAddCarBinding
import com.example.c_otomatch.models.Car
// IMPORT SEMUA FIREBASE
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.util.*

class SellCarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddCarBinding
    private var imageUri: Uri? = null

    // Firebase
    private lateinit var storage: FirebaseStorage
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var progressDialog: ProgressDialog

    // Launcher Gallery
    private val pickGallery =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                imageUri = uri
                binding.imgPreview.setImageURI(uri)
            }
        }

    // Launcher Camera
    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) {
                imageUri = getImageUriFromBitmap(bitmap)
                binding.imgPreview.setImageBitmap(bitmap)
            }
        }

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = android.provider.MediaStore.Images.Media.insertImage(
            contentResolver,
            bitmap,
            "Title_${System.currentTimeMillis()}",
            null
        )
        return Uri.parse(path)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = FirebaseStorage.getInstance()
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        progressDialog = ProgressDialog(this).apply {
            setTitle("Uploading...")
            setMessage("Harap tunggu...")
            setCancelable(false)
        }

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
                        uploadImageAndSaveCar()
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
        if (imageUri == null) {
            Toast.makeText(this, "Silakan pilih gambar mobil", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.etBrand.text.isNullOrBlank()) {
            binding.etBrand.error = "Merek wajib diisi"
            return false
        }
        if (binding.etModel.text.isNullOrBlank()) {
            binding.etModel.error = "Tipe/model wajib diisi"
            return false
        }
        return true
    }

    private fun generatePriceSuggestion(): String {
        val year = binding.etYear.text.toString().toIntOrNull() ?: 2020
        val mileageText = binding.etMileage.text.toString().replace("[^0-9]".toRegex(), "")
        val mileage = mileageText.toIntOrNull() ?: 0

        val base = when {
            year >= 2023 -> 350_000_000
            year >= 2019 -> 230_000_000
            year >= 2015 -> 150_000_000
            else -> 80_000_000
        }
        val discount = (mileage / 20_000) * 5_000_000
        val suggested = (base - discount).coerceAtLeast(20_000_000)
        return "Rp %,d".format(suggested)
    }

    private fun uploadImageAndSaveCar() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "Anda harus login untuk menjual", Toast.LENGTH_SHORT).show()
            return
        }

        progressDialog.show()

        val storageRef = storage.reference
        val imageFileName = "car_images/${System.currentTimeMillis()}_${UUID.randomUUID()}"
        val imageFileRef = storageRef.child(imageFileName)

        imageFileRef.putFile(imageUri!!)
            .addOnSuccessListener {
                imageFileRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    Toast.makeText(this, "Gambar diupload", Toast.LENGTH_SHORT).show()
                    saveCarToFirestore(downloadUri.toString())
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Gagal upload gambar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveCarToFirestore(imageUrl: String) {
        val priceText = if (binding.etPrice.text.isNullOrBlank())
            binding.tvGeneratedPrice.text.toString().replace("Harga saran: ", "")
        else
            binding.etPrice.text.toString()

        val newCar = Car(
            documentId = "", // Akan dibuat otomatis oleh Firestore
            id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            name = "${binding.etBrand.text} ${binding.etModel.text}",
            brand = binding.etBrand.text.toString(),
            year = binding.etYear.text.toString().toIntOrNull() ?: 2020,
            price = priceText,
            mileage = binding.etMileage.text.toString(),
            location = binding.etLocation.text.toString(),
            imageUrl = imageUrl,
            isWishlist = false,
            isSold = false,
            sellerName = binding.etSellerName.text.toString().ifEmpty { "Penjual" },
            sellerContact = auth.currentUser?.phoneNumber ?: "",
            bodyType = binding.etBodyType.text.toString(),
            color = binding.etColor.text.toString(),
            transmission = binding.etTransmission.text.toString(),
            fuel = binding.etFuelType.text.toString(),
            kmRange = "",
            sellerUid = auth.currentUser!!.uid // SIMPAN ID PENJUAL
        )

        db.collection("cars")
            .add(newCar)
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Mobil berhasil diposting!", Toast.LENGTH_LONG).show()

                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Gagal simpan data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}