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
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.c_otomatch.adapters.ImageSliderAdapter
import com.example.c_otomatch.databinding.ActivityAddCarBinding
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.utils.NumberTextWatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

class SellCarActivity : AppCompatActivity() {

    private val UPLOAD_PRESET = "OtoMatch_Preset" // Pake preset yang UNSIGNED

    private lateinit var binding: ActivityAddCarBinding

    // Variable buat nampung banyak foto
    private var selectedImageUris = mutableListOf<Uri>()
    private var uploadedImageUrls = mutableListOf<String>()

    // Buat nampung foto lama kalo lagi EDIT
    private var existingImageUrls = mutableListOf<String>()

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialog
    private var editingCarId: String? = null

    // Launcher Multiple Images (Galeri)
    private val pickMultipleImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris.addAll(uris)
            updateImagesPreview()
        }
    }

    // Launcher Kamera (Single)
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val uri = getImageUriFromBitmap(bitmap)
            if (uri != null) {
                selectedImageUris.add(uri)
                updateImagesPreview()
            }
        }
    }

    // Permission Launchers
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

        progressDialog = ProgressDialog(this).apply {
            setCancelable(false)
        }
        binding.rvSelectedImages.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        // Setup Dropdown
        setupDropdowns()

        val toolbar = findViewById<Toolbar>(R.id.toolbarSellCar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Cek apakah Mode EDIT atau BARU
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

        // Setup Listeners
        binding.etPrice.addTextChangedListener(NumberTextWatcher(binding.etPrice))
        binding.etMileage.addTextChangedListener(NumberTextWatcher(binding.etMileage, useCurrency = false))
        binding.etCapacity.addTextChangedListener(NumberTextWatcher(binding.etCapacity, useCurrency = false))

        binding.btnGallery.setOnClickListener { checkStoragePermission() }
        binding.btnCamera.setOnClickListener { checkCameraPermission() }

        binding.btnGeneratePrice.setOnClickListener {
            val year = binding.etYear.text.toString().toIntOrNull() ?: 2020
            val base = if (year >= 2020) 200_000_000 else 100_000_000
            val suggestion = "Rp %,d".format(base)
            binding.tvGeneratedPrice.text = "Harga saran: $suggestion"
            binding.etPrice.setText(suggestion.replace(Regex("[^0-9]"), ""))
        }

        binding.btnSubmit.setOnClickListener {
            if (validateInputs()) {
                val title = if (editingCarId != null) "Simpan Perubahan?" else "Posting Mobil?"
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage("Yakin data sudah benar?")
                    .setPositiveButton("Ya") { _, _ ->
                        // Mulai proses upload (recursive)
                        if (selectedImageUris.isNotEmpty()) {
                            uploadedImageUrls.clear() // Reset dulu
                            uploadImagesRecursive(0)
                        } else {
                            // Kalau edit dan gak ganti foto, langsung simpan data lama
                            saveData(existingImageUrls)
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
    }

    // Update tampilan slider setelah pilih foto
    private fun updateImagesPreview() {
        val displayList = mutableListOf<Any>()
        displayList.addAll(existingImageUrls) // Foto lama (URL)
        displayList.addAll(selectedImageUris) // Foto baru (Uri)

        val adapter = ImageSliderAdapter(displayList)
        binding.rvSelectedImages.adapter = adapter
        binding.tvPhotoCount.text = "${displayList.size} Foto dipilih"
        binding.tvPhotoCount.visibility = View.VISIBLE
    }

    // Fungsi Upload Rekursif (Satu per satu)
    private fun uploadImagesRecursive(index: Int) {
        if (index >= selectedImageUris.size) {
            // Selesai upload semua foto baru
            // Gabungkan foto lama + foto baru yg sudah jadi URL
            val finalUrls = mutableListOf<String>()
            finalUrls.addAll(existingImageUrls)
            finalUrls.addAll(uploadedImageUrls)

            saveData(finalUrls)
            return
        }

        progressDialog.setMessage("Mengupload foto ke-${index + 1} dari ${selectedImageUris.size}...")
        progressDialog.show()

        val uri = selectedImageUris[index]
        MediaManager.get().upload(uri).unsigned(UPLOAD_PRESET).callback(object : UploadCallback {
            override fun onStart(requestId: String) {}
            override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
            override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                var url = resultData["secure_url"] as String
                if (url.startsWith("http://")) url = url.replace("http://", "https://")

                uploadedImageUrls.add(url)
                // Lanjut ke foto berikutnya
                uploadImagesRecursive(index + 1)
            }
            override fun onError(requestId: String, error: ErrorInfo) {
                progressDialog.dismiss()
                Toast.makeText(this@SellCarActivity, "Gagal upload foto ke-${index+1}", Toast.LENGTH_SHORT).show()
            }
            override fun onReschedule(requestId: String, error: ErrorInfo) {}
        }).dispatch()
    }

    // Fungsi Simpan ke Firestore
    private fun saveData(finalImageUrls: List<String>) {
        progressDialog.setMessage("Menyimpan data...")

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

            // SIMPAN LIST URL
            "imageUrls" to finalImageUrls,
            // Thumbnail ambil yg pertama
            "imageUrl" to (finalImageUrls.firstOrNull() ?: ""),

            "sellerName" to binding.etSellerName.text.toString(),
            "sellerContact" to binding.etSellerContact.text.toString(),
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
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadCarDataToEdit(carId: String) {
        progressDialog.setMessage("Memuat data...")
        progressDialog.show()
        db.collection("cars").document(carId).get().addOnSuccessListener { document ->
            progressDialog.dismiss()
            val car = document.toObject(Car::class.java)
            if (car == null) { finish(); return@addOnSuccessListener }

            // Set Text
            binding.etSellerName.setText(car.sellerName)
            binding.etSellerContact.setText(car.sellerContact)
            binding.actSellerType.setText(car.sellerType, false)
            binding.etBrand.setText(car.brand)
            binding.etModel.setText(car.model)
            binding.etYear.setText(car.year.toString())
            binding.etMileage.setText(NumberTextWatcher.cleanDigits(car.mileage))
            binding.etLocation.setText(car.location)
            binding.etColor.setText(car.color)
            binding.etVariant.setText(car.variant)
            binding.actFuelType.setText(car.fuel, false)
            binding.actTransmission.setText(car.transmission, false)
            binding.actBodyType.setText(car.bodyType, false)
            binding.etCapacity.setText(NumberTextWatcher.cleanDigits(car.capacity))
            binding.etNegatives.setText(car.negatives)
            binding.etMods.setText(car.mods)
            binding.etPrice.setText(NumberTextWatcher.cleanDigits(car.price))

            // Load Foto
            if (car.imageUrls.isNotEmpty()) {
                existingImageUrls.addAll(car.imageUrls)
            } else if (car.imageUrl.isNotEmpty()) {
                existingImageUrls.add(car.imageUrl)
            }
            updateImagesPreview()
        }
    }

    private fun validateInputs(): Boolean {
        if (selectedImageUris.isEmpty() && existingImageUrls.isEmpty()) {
            Toast.makeText(this, "Wajib upload minimal 1 foto!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.etBrand.text.isNullOrBlank()) {
            binding.etBrand.error = "Wajib diisi"
            return false
        }
        if (binding.etPrice.text.isNullOrBlank()) {
            binding.etPrice.error = "Wajib diisi"
            return false
        }
        return true
    }

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "Title_${System.currentTimeMillis()}", null)
        return if (path != null) Uri.parse(path) else null
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePhoto.launch(null)
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickMultipleImages.launch("image/*")
        } else {
            requestStoragePermissionLauncher.launch(permission)
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

    private fun generatePriceSuggestion(): String {
        val year = binding.etYear.text.toString().toIntOrNull() ?: 2020
        val base = if (year >= 2020) 200_000_000 else 100_000_000
        return "Rp %,d".format(base)
    }
}