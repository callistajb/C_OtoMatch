package com.example.c_otomatch

import android.Manifest // <-- IMPORT BUAT IZIN
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager // <-- IMPORT BUAT IZIN
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build // <-- IMPORT BARU
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat // <-- IMPORT BUAT IZIN
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

    // Ganti ini pake key Cloudinary-mu
    private val CLOUD_NAME = "dqehqqz7q"
    private val UPLOAD_PRESET = "OtoMatch_preset"

    private lateinit var binding: ActivityAddCarBinding
    private var imageUri: Uri? = null // Ini buat nampung URI gambar BARU
    private var existingImageUrl: String? = null // Ini buat nampung URL gambar LAMA (pas edit)

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialog

    // Cek kita lagi mode 'Edit' atau 'Jual Baru'
    private var editingCarId: String? = null

    // --- ⬇️ LAUNCHER IZIN (UDAH BENER) ⬇️ ---
    // Launcher buat minta izin KAMERA
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("SellCarActivity", "Izin kamera dikasih, buka kamera.")
            takePhoto.launch(null)
        } else {
            Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher buat minta izin STORAGE (GALERI)
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("SellCarActivity", "Izin galeri dikasih, buka galeri.")
            pickGallery.launch("image/*")
        } else {
            Toast.makeText(this, "Izin galeri ditolak", Toast.LENGTH_SHORT).show()
        }
    }
    // --- ⬆️ SELESAI LAUNCHER IZIN ⬆️ ---

    // Launcher ambil gambar dari Galeri
    private val pickGallery =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                Log.d("SellCarActivity", "Gambar dipilih dari galeri: $uri")
                imageUri = uri // Simpen URI gambar BARU
                binding.imgPreview.setImageURI(uri) // Tampilin
            }
        }
    // Launcher ambil foto dari Kamera
    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) {
                Log.d("SellCarActivity", "Gambar diambil dari kamera")
                imageUri = getImageUriFromBitmap(bitmap) // Simpen URI gambar BARU
                binding.imgPreview.setImageBitmap(bitmap) // Tampilin
            }
        }

    // Helper buat convert Bitmap (dari kamera) ke Uri
    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(
            contentResolver,
            bitmap,
            "Title_${System.currentTimeMillis()}",
            null
        )
        return if (path != null) Uri.parse(path) else null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init service
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        MediaManager.init(this, hashMapOf("cloud_name" to CLOUD_NAME))

        progressDialog = ProgressDialog(this).apply {
            setTitle("Uploading...")
            setMessage("Harap tunggu...")
            setCancelable(false)
        }

        // Setup Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbarSellCar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Cek kita lagi mode 'Edit' atau 'Jual Baru'
        editingCarId = intent.getStringExtra("EDIT_CAR_ID")

        if (editingCarId != null) {
            // --- INI MODE EDIT ---
            Log.d("SellCarActivity", "Mode EDIT, ID: $editingCarId")
            binding.toolbarSellCar.title = "Edit Mobil"
            binding.btnSubmit.text = "Simpan Perubahan"
            loadCarDataToEdit(editingCarId!!)
        } else {
            // --- INI MODE JUAL BARU ---
            Log.d("SellCarActivity", "Mode JUAL BARU")
            binding.toolbarSellCar.title = "Jual Mobil"
            binding.btnSubmit.text = "Submit & Post"
            // Ambil data user, isi otomatis form-nya
            loadUserData()
        }

        // Pasang formatter angka otomatis
        binding.etPrice.addTextChangedListener(NumberTextWatcher(binding.etPrice))
        binding.etMileage.addTextChangedListener(NumberTextWatcher(binding.etMileage, useCurrency = false))
        binding.etCapacity.addTextChangedListener(NumberTextWatcher(binding.etCapacity, useCurrency = false))

        binding.btnGallery.setOnClickListener {
            checkStoragePermission()
        }

        binding.btnCamera.setOnClickListener {
            checkCameraPermission()
        }

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
                    .setMessage("Yakin ingin menjual mobil ini?")
                    .setPositiveButton("Ya") { _, _ ->
                        uploadImageAndSaveCar()
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Izin udah ada, gaskeun
                Log.d("SellCarActivity", "Izin kamera sudah ada.")
                takePhoto.launch(null)
            }
            // (Opsional) Kalo mau kasih penjelasan dulu kenapa butuh izin
            // shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> { ... }
            else -> {
                // Belum ada izin, minta dulu
                Log.d("SellCarActivity", "Minta izin kamera...")
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun checkStoragePermission() {
        // Cek versi Android-nya
        val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 (SDK 33) ke atas, minta izin spesifik FOTO
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // Android 12 (SDK 32) ke bawah, masih pake izin lama
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(
                this,
                permissionToRequest
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Izin udah ada, gaskeun
                Log.d("SellCarActivity", "Izin galeri sudah ada.")
                pickGallery.launch("image/*")
            }
            else -> {
                // Belum ada izin, minta dulu
                Log.d("SellCarActivity", "Minta izin galeri: $permissionToRequest")
                requestStoragePermissionLauncher.launch(permissionToRequest)
            }
        }
    }
    // --- ⬆️ SELESAI FUNGSI BARU ⬆️ ---

    // Ambil data user dari Firestore buat isi form (Mode JUAL BARU)
    private fun loadUserData() {
        // (Tidak berubah)
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        binding.etSellerName.setText(document.getString("name"))
                        binding.etSellerContact.setText(document.getString("phone"))
                    } else {
                        Log.w("SellCarActivity", "Dokumen user tidak ditemukan")
                    }
                }
                .addOnFailureListener {
                    Log.w("SellCarActivity", "Gagal mengambil data user")
                }
        }
    }

    // Ambil data mobil dari Firestore buat isi form (Mode EDIT)
    private fun loadCarDataToEdit(carId: String) {
        // (Tidak berubah)
        progressDialog.setMessage("Memuat data mobil...")
        progressDialog.show()

        db.collection("cars").document(carId)
            .get()
            .addOnSuccessListener { document ->
                progressDialog.dismiss()
                val car = document.toObject(Car::class.java)
                if (car == null) {
                    Toast.makeText(this, "Error: Data mobil tidak ditemukan.", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                binding.etSellerName.setText(car.sellerName)
                binding.etSellerContact.setText(car.sellerContact)
                binding.etSellerType.setText(car.sellerType)
                binding.etBrand.setText(car.brand)
                binding.etModel.setText(car.model)
                binding.etVariant.setText(car.variant)
                binding.etYear.setText(car.year.toString())
                binding.etColor.setText(car.color)
                binding.etMileage.setText(NumberTextWatcher.cleanDigits(car.mileage))
                binding.etFuelType.setText(car.fuel)
                binding.etTransmission.setText(car.transmission)
                binding.etBodyType.setText(car.bodyType)
                binding.etCapacity.setText(NumberTextWatcher.cleanDigits(car.capacity))
                binding.etNegatives.setText(car.negatives)
                binding.etMods.setText(car.mods)
                binding.etPrice.setText(NumberTextWatcher.cleanDigits(car.price))
                binding.etLocation.setText(car.location)

                existingImageUrl = car.imageUrl
                if (existingImageUrl!!.isNotEmpty()) {
                    Glide.with(this)
                        .load(existingImageUrl)
                        .placeholder(R.drawable.ic_car)
                        .error(R.drawable.ic_car)
                        .into(binding.imgPreview)
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Gagal memuat data: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun validateInputs(): Boolean {
        // (Tidak berubah)
        if (imageUri == null && existingImageUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Silakan pilih gambar mobil", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.etSellerName.text.isNullOrBlank()) {
            binding.etSellerName.error = "Nama penjual wajib diisi"
            binding.etSellerName.requestFocus()
            return false
        }
        if (binding.etSellerContact.text.isNullOrBlank()) {
            binding.etSellerContact.error = "Kontak penjual wajib diisi"
            binding.etSellerContact.requestFocus()
            return false
        }
        if (binding.etBrand.text.isNullOrBlank()) {
            binding.etBrand.error = "Merek wajib diisi"
            binding.etBrand.requestFocus()
            return false
        }
        if (binding.etModel.text.isNullOrBlank()) {
            binding.etModel.error = "Tipe/model wajib diisi"
            binding.etModel.requestFocus()
            return false
        }
        if (binding.etYear.text.isNullOrBlank()) {
            binding.etYear.error = "Tahun wajib diisi"
            binding.etYear.requestFocus()
            return false
        }
        if (binding.etMileage.text.isNullOrBlank()) {
            binding.etMileage.error = "Jarak tempuh wajib diisi"
            binding.etMileage.requestFocus()
            return false
        }
        if (binding.etLocation.text.isNullOrBlank()) {
            binding.etLocation.error = "Lokasi wajib diisi"
            binding.etLocation.requestFocus()
            return false
        }
        if (binding.etColor.text.isNullOrBlank()) {
            binding.etColor.error = "Warna wajib diisi"
            binding.etColor.requestFocus()
            return false
        }
        if (binding.etPrice.text.isNullOrBlank() || NumberTextWatcher.cleanDigits(binding.etPrice.text.toString()).toLongOrNull() == 0L) {
            binding.etPrice.error = "Harga wajib diisi"
            binding.etPrice.requestFocus()
            return false
        }
        return true
    }

    private fun generatePriceSuggestion(): String {
        // (Tidak berubah)
        val year = binding.etYear.text.toString().toIntOrNull() ?: 2020
        val mileageText = NumberTextWatcher.cleanDigits(binding.etMileage.text.toString())
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

    // 1. Cek dulu perlu upload gambar baru apa ngga
    private fun uploadImageAndSaveCar() {
        // (Tidak berubah)
        if (auth.currentUser == null) {
            Toast.makeText(this, "Sesi login habis, silakan login ulang", Toast.LENGTH_SHORT).show()
            return
        }

        progressDialog.show()

        if (imageUri != null) {
            progressDialog.setMessage("Mengupload gambar baru...")
            MediaManager.get().upload(imageUri!!)
                .unsigned(UPLOAD_PRESET)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val newImageUrl = resultData["secure_url"] as String
                        Log.d("SellCarActivity", "Gambar baru diupload ke: $newImageUrl")
                        saveData(newImageUrl) // Simpen data pake URL baru
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        progressDialog.dismiss()
                        Log.e("SellCarActivity", "Cloudinary Upload Error: ${error.description}")
                        Toast.makeText(this@SellCarActivity, "Gagal upload gambar: ${error.description}", Toast.LENGTH_LONG).show()
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                })
                .dispatch()
        } else {
            Log.d("SellCarActivity", "Gambar tidak diubah.")
            saveData(existingImageUrl!!)
        }
    }

    // 2. Fungsi ini nentuin mau CREATE data baru atau UPDATE data lama
    private fun saveData(finalImageUrl: String) {
        // (Tidak berubah)
        val priceString = NumberTextWatcher.cleanDigits(binding.etPrice.text.toString())
        val mileageString = NumberTextWatcher.cleanDigits(binding.etMileage.text.toString())
        val capacityString = NumberTextWatcher.cleanDigits(binding.etCapacity.text.toString())
        val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))

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
            "sellerType" to binding.etSellerType.text.toString(),
            "bodyType" to binding.etBodyType.text.toString(),
            "color" to binding.etColor.text.toString(),
            "transmission" to binding.etTransmission.text.toString(),
            "fuel" to binding.etFuelType.text.toString(),
            "capacity" to if (capacityString.isEmpty()) "" else NumberTextWatcher.formatToCc(capacityString.toLongOrNull() ?: 0),
            "variant" to binding.etVariant.text.toString(),
            "negatives" to binding.etNegatives.text.toString(),
            "mods" to binding.etMods.text.toString()
        )

        if (editingCarId != null) {
            progressDialog.setMessage("Menyimpan perubahan...")
            db.collection("cars").document(editingCarId!!)
                .update(carDataMap)
                .addOnSuccessListener {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Mobil berhasil diperbarui!", Toast.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    Toast.makeText(this, "Gagal update data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            progressDialog.setMessage("Menyimpan data mobil...")

            carDataMap["isSold"] = false
            carDataMap["sellerUid"] = auth.currentUser!!.uid
            carDataMap["createdAt"] = FieldValue.serverTimestamp()

            db.collection("cars")
                .add(carDataMap)
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
}