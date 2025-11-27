package com.example.c_otomatch

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.c_otomatch.databinding.ActivityAddCarBinding
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.utils.NumberTextWatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.util.*

class SellCarActivity : AppCompatActivity() {

    private val UPLOAD_PRESET = "OtoMatch_preset"

    private lateinit var binding: ActivityAddCarBinding
    private var imageUri: Uri? = null
    private var existingImageUrl: String? = null

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialog

    private var editingCarId: String? = null

    // ... (Launcher Permission code tetap sama) ...
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) takePhoto.launch(null)
        else Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) pickGallery.launch("image/*")
        else Toast.makeText(this, "Izin galeri ditolak", Toast.LENGTH_SHORT).show()
    }

    private val pickGallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            binding.imgPreview.setImageURI(uri)
        }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            imageUri = getImageUriFromBitmap(bitmap)
            binding.imgPreview.setImageBitmap(bitmap)
        }
    }

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "Title_${System.currentTimeMillis()}", null)
        return if (path != null) Uri.parse(path) else null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        progressDialog = ProgressDialog(this).apply {
            setTitle("Uploading...")
            setMessage("Harap tunggu...")
            setCancelable(false)
        }

        // Setup Dropdown Pilihan
        setupDropdowns()

        val toolbar = findViewById<Toolbar>(R.id.toolbarSellCar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        editingCarId = intent.getStringExtra("EDIT_CAR_ID")

        if (editingCarId != null) {
            binding.toolbarSellCar.title = "Edit Mobil"
            binding.btnSubmit.text = "Simpan Perubahan"
            loadCarDataToEdit(editingCarId!!)
        } else {
            binding.toolbarSellCar.title = "Jual Mobil"
            binding.btnSubmit.text = "Submit & Post"
            loadUserData()
        }

        binding.etPrice.addTextChangedListener(NumberTextWatcher(binding.etPrice))
        binding.etMileage.addTextChangedListener(NumberTextWatcher(binding.etMileage, useCurrency = false))
        binding.etCapacity.addTextChangedListener(NumberTextWatcher(binding.etCapacity, useCurrency = false))

        binding.btnGallery.setOnClickListener { checkStoragePermission() }
        binding.btnCamera.setOnClickListener { checkCameraPermission() }

        binding.btnGeneratePrice.setOnClickListener {
            val saran = generatePriceSuggestion()
            binding.tvGeneratedPrice.text = "Harga saran: $saran"
            binding.etPrice.setText(saran.replace(Regex("[^0-9]"), ""))
        }

        binding.btnSubmit.setOnClickListener {
            if (validateInputs()) {
                val title = if (editingCarId != null) "Simpan Perubahan?" else "Posting Mobil?"
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage("Yakin data sudah benar?")
                    .setPositiveButton("Ya") { _, _ -> uploadImageAndSaveCar() }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
    }

    // Fungsi setup Dropdown (Permintaan Pak Alex #2)
    private fun setupDropdowns() {
        val sellerTypes = arrayOf("Individu", "Diler")
        val fuelTypes = arrayOf("Bensin", "Diesel", "Listrik", "Hybrid")
        val transmissions = arrayOf("Manual", "Automatic", "CVT")
        val bodyTypes = arrayOf("SUV", "MPV", "Sedan", "Hatchback", "Coupe", "Van", "Pickup")

        // Pastikan ID di XML nanti adalah AutoCompleteTextView, bukan EditText biasa
        binding.actSellerType.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sellerTypes))
        binding.actFuelType.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, fuelTypes))
        binding.actTransmission.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, transmissions))
        binding.actBodyType.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bodyTypes))
    }

    // ... (Fungsi checkCameraPermission, checkStoragePermission, loadUserData tetap sama) ...
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePhoto.launch(null)
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkStoragePermission() {
        val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permissionToRequest) == PackageManager.PERMISSION_GRANTED) {
            pickGallery.launch("image/*")
        } else {
            requestStoragePermissionLauncher.launch(permissionToRequest)
        }
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        binding.etSellerName.setText(document.getString("name"))
                        binding.etSellerContact.setText(document.getString("phone"))
                    }
                }
        }
    }

    private fun loadCarDataToEdit(carId: String) {
        progressDialog.setMessage("Memuat data mobil...")
        progressDialog.show()

        db.collection("cars").document(carId).get()
            .addOnSuccessListener { document ->
                progressDialog.dismiss()
                val car = document.toObject(Car::class.java)
                if (car == null) {
                    finish()
                    return@addOnSuccessListener
                }

                binding.etSellerName.setText(car.sellerName)
                binding.etSellerContact.setText(car.sellerContact)

                // Set value dropdown
                binding.actSellerType.setText(car.sellerType, false)
                binding.actFuelType.setText(car.fuel, false)
                binding.actTransmission.setText(car.transmission, false)
                binding.actBodyType.setText(car.bodyType, false)

                binding.etBrand.setText(car.brand)
                binding.etModel.setText(car.model)
                binding.etVariant.setText(car.variant)
                binding.etYear.setText(car.year.toString())
                binding.etColor.setText(car.color)
                binding.etMileage.setText(NumberTextWatcher.cleanDigits(car.mileage))
                binding.etCapacity.setText(NumberTextWatcher.cleanDigits(car.capacity))
                binding.etNegatives.setText(car.negatives)
                binding.etMods.setText(car.mods)
                binding.etPrice.setText(NumberTextWatcher.cleanDigits(car.price))
                binding.etLocation.setText(car.location)

                existingImageUrl = car.imageUrl
                if (existingImageUrl!!.isNotEmpty()) {
                    Glide.with(this).load(existingImageUrl).into(binding.imgPreview)
                }
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                finish()
            }
    }

    private fun validateInputs(): Boolean {
        if (imageUri == null && existingImageUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Wajib upload foto mobil!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.etBrand.text.isNullOrBlank()) return false
        if (binding.etPrice.text.isNullOrBlank()) return false
        // Tambahkan validasi lain sesuai kebutuhan
        return true
    }

    private fun generatePriceSuggestion(): String {
        val year = binding.etYear.text.toString().toIntOrNull() ?: 2020
        val base = if (year >= 2020) 200_000_000 else 100_000_000
        return "Rp %,d".format(base)
    }

    private fun uploadImageAndSaveCar() {
        if (auth.currentUser == null) return
        progressDialog.show()

        if (imageUri != null) {
            progressDialog.setMessage("Mengupload gambar...")
            MediaManager.get().upload(imageUri!!).unsigned(UPLOAD_PRESET).callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    // Fix URL http ke https
                    var newImageUrl = resultData["secure_url"] as String
                    if (newImageUrl.startsWith("http://")) {
                        newImageUrl = newImageUrl.replace("http://", "https://")
                    }
                    Log.d("SellCarActivity", "URL Gambar Akhir: $newImageUrl")
                    saveData(newImageUrl)
                }
                override fun onError(requestId: String, error: ErrorInfo) {
                    progressDialog.dismiss()
                    Toast.makeText(this@SellCarActivity, "Gagal upload: ${error.description}", Toast.LENGTH_LONG).show()
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            }).dispatch()
        } else {
            saveData(existingImageUrl!!)
        }
    }

    private fun saveData(finalImageUrl: String) {
        val priceString = NumberTextWatcher.cleanDigits(binding.etPrice.text.toString())
        val mileageString = NumberTextWatcher.cleanDigits(binding.etMileage.text.toString())
        val capacityString = NumberTextWatcher.cleanDigits(binding.etCapacity.text.toString())

        val carDataMap = hashMapOf<String, Any>(
            "name" to "${binding.etBrand.text} ${binding.etModel.text}",
            "brand" to binding.etBrand.text.toString(),
            "model" to binding.etModel.text.toString(),
            "year" to (binding.etYear.text.toString().toIntOrNull() ?: 2020),
            "price" to NumberTextWatcher.formatToRupiah(priceString.toLongOrNull() ?: 0),
            "mileage" to NumberTextWatcher.formatToKm(mileageString.toLongOrNull() ?: 0),
            "location" to binding.etLocation.text.toString(),
            "imageUrl" to finalImageUrl,
            "sellerName" to binding.etSellerName.text.toString(),
            "sellerContact" to binding.etSellerContact.text.toString(),

            // Ambil value dari Dropdown (AutoCompleteTextView)
            "sellerType" to binding.actSellerType.text.toString(),
            "fuel" to binding.actFuelType.text.toString(),
            "transmission" to binding.actTransmission.text.toString(),
            "bodyType" to binding.actBodyType.text.toString(),

            "color" to binding.etColor.text.toString(),
            "capacity" to if (capacityString.isEmpty()) "" else NumberTextWatcher.formatToCc(capacityString.toLongOrNull() ?: 0),
            "variant" to binding.etVariant.text.toString(),
            "negatives" to binding.etNegatives.text.toString(),
            "mods" to binding.etMods.text.toString()
        )

        if (editingCarId != null) {
            db.collection("cars").document(editingCarId!!).update(carDataMap)
                .addOnSuccessListener {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
        } else {
            carDataMap["isSold"] = false
            carDataMap["sellerUid"] = auth.currentUser!!.uid
            carDataMap["createdAt"] = FieldValue.serverTimestamp()

            db.collection("cars").add(carDataMap)
                .addOnSuccessListener {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Berhasil diposting!", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                .addOnFailureListener {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Gagal posting ke database.", Toast.LENGTH_SHORT).show()
                }
        }
    }
}