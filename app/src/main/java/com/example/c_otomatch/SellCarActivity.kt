package com.example.c_otomatch

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.c_otomatch.adapters.ImageSliderAdapter
import com.example.c_otomatch.databinding.ActivityAddCarBinding
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.utils.NumberTextWatcher
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SellCarActivity : AppCompatActivity() {

    private val UPLOAD_PRESET = "OtoMatch_Preset"
    private lateinit var binding: ActivityAddCarBinding
    private var selectedImageUris = mutableListOf<Uri>()
    private var uploadedImageUrls = mutableListOf<String>()
    private var existingImageUrls = mutableListOf<String>()

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Ganti ProgressDialog dengan AlertDialog
    private lateinit var loadingDialog: AlertDialog
    private var editingCarId: String? = null

    private val pickMultipleImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris.addAll(uris)
            updateImagesPreview()
        }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            // Gunakan fungsi save ke cache (lebih aman dan modern)
            val uri = saveBitmapToCache(this, bitmap)
            if (uri != null) {
                selectedImageUris.add(uri)
                updateImagesPreview()
            } else {
                Toast.makeText(this, "Gagal menyimpan gambar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) takePhoto.launch(null)
        else Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) pickMultipleImages.launch("image/*")
        else Toast.makeText(this, "Izin galeri ditolak", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupLoadingDialog()

        setSupportActionBar(binding.toolbarSellCar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarSellCar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        setupDropdowns()
        binding.rvSelectedImages.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        editingCarId = intent.getStringExtra("EDIT_CAR_ID")
        if (editingCarId != null) {
            binding.toolbarSellCar.title = "Edit Mobil"
            binding.btnSubmit.text = "Simpan Perubahan"
            loadCarDataToEdit(editingCarId!!)
        } else {
            loadUserData()
        }

        binding.etPrice.addTextChangedListener(NumberTextWatcher(binding.etPrice))
        binding.etMileage.addTextChangedListener(NumberTextWatcher(binding.etMileage, useCurrency = false))
        binding.etCapacity.addTextChangedListener(NumberTextWatcher(binding.etCapacity, useCurrency = false))

        binding.btnGallery.setOnClickListener { checkStoragePermission() }
        binding.btnCamera.setOnClickListener { checkCameraPermission() }

        binding.btnGeneratePrice.setOnClickListener {
            generatePriceSuggestion()
        }

        binding.btnSubmit.setOnClickListener {
            if (validateInputs()) {
                val message = if (editingCarId != null) "Simpan perubahan?" else "Posting mobil?"
                AlertDialog.Builder(this)
                    .setTitle("Konfirmasi")
                    .setMessage(message)
                    .setPositiveButton("Ya") { _, _ ->
                        if (selectedImageUris.isNotEmpty()) {
                            uploadedImageUrls.clear()
                            uploadImagesRecursive(0)
                        } else {
                            saveData(existingImageUrls)
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
    }

    // --- SETUP LOADING DIALOG PENGGANTI PROGRESSDIALOG ---
    private fun setupLoadingDialog() {
        val llPadding = 30
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.HORIZONTAL
        ll.setPadding(llPadding, llPadding, llPadding, llPadding)
        ll.gravity = Gravity.CENTER
        var llParam = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        llParam.gravity = Gravity.CENTER
        ll.layoutParams = llParam

        val progressBar = ProgressBar(this)
        progressBar.isIndeterminate = true
        progressBar.setPadding(0, 0, llPadding, 0)
        progressBar.layoutParams = llParam

        llParam = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        llParam.gravity = Gravity.CENTER
        val tvText = TextView(this)
        tvText.text = "Loading..."
        tvText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        tvText.textSize = 16f
        tvText.layoutParams = llParam

        ll.addView(progressBar)
        ll.addView(tvText)

        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setView(ll)

        loadingDialog = builder.create()
    }

    private fun showLoading(message: String) {
        // Karena kita pakai custom view sederhana, kita set title saja atau biarkan default "Loading..."
        loadingDialog.setTitle(message)
        if (!loadingDialog.isShowing) loadingDialog.show()
    }

    private fun hideLoading() {
        if (loadingDialog.isShowing) loadingDialog.dismiss()
    }
    // ----------------------------------------------------

    private fun updateImagesPreview() {
        val displayList = mutableListOf<Any>()
        displayList.addAll(existingImageUrls)
        displayList.addAll(selectedImageUris)

        val adapter = ImageSliderAdapter(displayList)
        binding.rvSelectedImages.adapter = adapter
        binding.tvPhotoCount.text = getString(R.string.photo_count_format, displayList.size)
        binding.tvPhotoCount.visibility = View.VISIBLE
    }

    private fun uploadImagesRecursive(index: Int) {
        if (index >= selectedImageUris.size) {
            val finalUrls = mutableListOf<String>()
            finalUrls.addAll(existingImageUrls)
            finalUrls.addAll(uploadedImageUrls)
            saveData(finalUrls)
            return
        }

        showLoading("Mengupload foto ${index + 1}/${selectedImageUris.size}...")

        MediaManager.get().upload(selectedImageUris[index])
            .unsigned(UPLOAD_PRESET)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    var url = resultData["secure_url"] as String
                    if (url.startsWith("http://")) url = url.replace("http://", "https://")
                    uploadedImageUrls.add(url)
                    uploadImagesRecursive(index + 1)
                }
                override fun onError(requestId: String, error: ErrorInfo) {
                    hideLoading()
                    Toast.makeText(this@SellCarActivity, "Gagal upload: ${error.description}", Toast.LENGTH_SHORT).show()
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            }).dispatch()
    }

    private fun saveData(imageUrls: List<String>) {
        showLoading("Menyimpan data...")

        val priceRaw = NumberTextWatcher.cleanDigits(binding.etPrice.text.toString())
        val mileageRaw = NumberTextWatcher.cleanDigits(binding.etMileage.text.toString())
        val capacityRaw = NumberTextWatcher.cleanDigits(binding.etCapacity.text.toString())

        val carData = hashMapOf<String, Any>(
            "name" to "${binding.etBrand.text} ${binding.etModel.text}",
            "brand" to binding.etBrand.text.toString(),
            "model" to binding.etModel.text.toString(),
            "year" to (binding.etYear.text.toString().toIntOrNull() ?: 0),
            "price" to NumberTextWatcher.formatToRupiah(priceRaw.toLongOrNull() ?: 0),
            "mileage" to NumberTextWatcher.formatToKm(mileageRaw.toLongOrNull() ?: 0),
            "capacity" to NumberTextWatcher.formatToCc(capacityRaw.toLongOrNull() ?: 0),
            "location" to binding.etLocation.text.toString(),
            "color" to binding.etColor.text.toString(),
            "variant" to binding.etVariant.text.toString(),
            "fuel" to binding.actFuelType.text.toString(),
            "transmission" to binding.actTransmission.text.toString(),
            "bodyType" to binding.actBodyType.text.toString(),
            "sellerName" to binding.etSellerName.text.toString(),
            "sellerContact" to binding.etSellerContact.text.toString(),
            "sellerType" to binding.actSellerType.text.toString(),
            "negatives" to binding.etNegatives.text.toString(),
            "mods" to binding.etMods.text.toString(),
            "imageUrls" to imageUrls,
            "imageUrl" to (imageUrls.firstOrNull() ?: "")
        )

        if (editingCarId != null) {
            db.collection("cars").document(editingCarId!!)
                .update(carData)
                .addOnSuccessListener {
                    hideLoading()
                    Toast.makeText(this, "Berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                .addOnFailureListener {
                    hideLoading()
                    Toast.makeText(this, "Gagal update: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            carData["isSold"] = false
            carData["createdAt"] = FieldValue.serverTimestamp()
            carData["sellerUid"] = auth.currentUser?.uid ?: ""

            db.collection("cars").add(carData)
                .addOnSuccessListener {
                    hideLoading()
                    Toast.makeText(this, "Berhasil diposting!", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                .addOnFailureListener {
                    hideLoading()
                    Toast.makeText(this, "Gagal posting: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadCarDataToEdit(carId: String) {
        showLoading("Memuat data...")
        db.collection("cars").document(carId).get().addOnSuccessListener { doc ->
            hideLoading()
            val car = doc.toObject(Car::class.java)
            if (car != null) {
                binding.etBrand.setText(car.brand)
                binding.etModel.setText(car.model)
                binding.etYear.setText(car.year.toString())
                binding.etPrice.setText(NumberTextWatcher.cleanDigits(car.price))
                binding.etMileage.setText(NumberTextWatcher.cleanDigits(car.mileage))
                binding.etLocation.setText(car.location)
                binding.etColor.setText(car.color)
                binding.etVariant.setText(car.variant)
                binding.etCapacity.setText(NumberTextWatcher.cleanDigits(car.capacity))
                binding.etSellerName.setText(car.sellerName)
                binding.etSellerContact.setText(car.sellerContact)
                binding.etNegatives.setText(car.negatives)
                binding.etMods.setText(car.mods)

                binding.actFuelType.setText(car.fuel, false)
                binding.actTransmission.setText(car.transmission, false)
                binding.actBodyType.setText(car.bodyType, false)
                binding.actSellerType.setText(car.sellerType, false)

                existingImageUrls.clear()
                if (car.imageUrls.isNotEmpty()) {
                    existingImageUrls.addAll(car.imageUrls)
                } else if (car.imageUrl.isNotEmpty()) {
                    existingImageUrls.add(car.imageUrl)
                }
                updateImagesPreview()
            }
        }
    }

    private fun validateInputs(): Boolean {
        if (selectedImageUris.isEmpty() && existingImageUrls.isEmpty()) {
            Toast.makeText(this, "Minimal upload 1 foto!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.etBrand.text.isNullOrBlank() || binding.etPrice.text.isNullOrBlank()) {
            Toast.makeText(this, "Merek dan Harga wajib diisi!", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    // Fungsi pengganti MediaStore.insertImage (Menyimpan ke Cache App)
    private fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri? {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        return try {
            val stream = FileOutputStream("$cachePath/image_${System.currentTimeMillis()}.jpg")
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.close()
            val imageFile = File(cachePath, "image_${System.currentTimeMillis()}.jpg")
            // Menggunakan FileProvider untuk keamanan (pastikan provider ada di Manifest, atau gunakan Uri.fromFile untuk internal use simple)
            // Untuk simplifikasi internal app use, Uri.fromFile sudah cukup jika tidak dishare ke app lain
            Uri.fromFile(imageFile)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePhoto.launch(null)
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkStoragePermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            pickMultipleImages.launch("image/*")
        } else {
            requestStoragePermissionLauncher.launch(perm)
        }
    }

    private fun loadUserData() {
        auth.currentUser?.let { user ->
            db.collection("users").document(user.uid).get().addOnSuccessListener {
                binding.etSellerName.setText(it.getString("name"))
                binding.etSellerContact.setText(it.getString("phone"))
            }
        }
    }

    private fun setupDropdowns() {
        val sellerTypes = arrayOf("Individu", "Diler")
        val fuelTypes = arrayOf("Bensin", "Diesel", "Listrik", "Hybrid")
        val transmissions = arrayOf("Manual", "Automatic", "CVT")
        val bodyTypes = arrayOf("SUV", "MPV", "Sedan", "Hatchback", "Coupe", "Van", "Pickup")

        binding.actSellerType.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sellerTypes))
        binding.actFuelType.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, fuelTypes))
        binding.actTransmission.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, transmissions))
        binding.actBodyType.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bodyTypes))
    }

    // Fungsi generate harga dipanggil via tombol
    private fun generatePriceSuggestion() {
        val year = binding.etYear.text.toString().toIntOrNull() ?: 2020
        val base = if (year >= 2020) 200_000_000 else 100_000_000
        val suggestion = "Rp %,d".format(base)
        binding.tvGeneratedPrice.text = getString(R.string.price_suggestion_format, suggestion)
        binding.etPrice.setText(suggestion.replace(Regex("[^0-9]"), ""))
    }
}