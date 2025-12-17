package com.example.c_otomatch

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.c_otomatch.adapters.ImageSliderAdapter
import com.example.c_otomatch.databinding.ActivityAddCarBinding
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.utils.CarPriceModelHelper
import com.example.c_otomatch.utils.ImageClassifierHelper
import com.example.c_otomatch.utils.NumberTextWatcher
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.util.Locale

class SellCarActivity : AppCompatActivity() {

    private val UPLOAD_PRESET = "OtoMatch_Preset"
    private lateinit var binding: ActivityAddCarBinding

    // List utama untuk Adapter (Menampung String URL & Uri Lokal) - HARUS MutableList<Any>
    private var displayImages = mutableListOf<Any>()

    // List terpisah untuk manajemen upload
    private var selectedImageUris = mutableListOf<Uri>() // Foto baru
    private var existingImageUrls = mutableListOf<String>() // Foto lama (saat edit)
    private var uploadedImageUrls = mutableListOf<String>() // Hasil upload

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialog
    private var editingCarId: String? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // HELPER MACHINE LEARNING (OFFLINE TFLITE)
    private lateinit var priceModelHelper: CarPriceModelHelper
    private lateinit var imageClassifierHelper: ImageClassifierHelper

    private val cityList = listOf(
        "Jakarta Pusat", "Jakarta Selatan", "Jakarta Barat", "Jakarta Timur", "Jakarta Utara",
        "Tangerang", "Tangerang Selatan", "Bekasi", "Depok", "Bogor",
        "Bandung", "Surabaya", "Semarang", "Yogyakarta", "Solo", "Malang", "Denpasar", "Bali",
        "Medan", "Palembang", "Makassar", "Balikpapan", "Samarinda", "Pontianak", "Banjarmasin"
    )

    private val carDataMap = mapOf(
        "Toyota" to listOf("Avanza", "Veloz", "Innova Zenix", "Fortuner", "Rush", "Agya", "Calya", "Yaris", "Vios", "Camry", "Alphard"),
        "Honda" to listOf("Brio", "Jazz", "HR-V", "CR-V", "BR-V", "WR-V", "Mobilio", "City", "Civic", "Accord"),
        "Daihatsu" to listOf("Xenia", "Terios", "Ayla", "Sigra", "Gran Max", "Luxio", "Sirion", "Rocky"),
        "Suzuki" to listOf("Ertiga", "XL7", "Ignis", "Baleno", "S-Cross", "Jimny", "APV"),
        "Mitsubishi" to listOf("Xpander", "Pajero Sport", "Triton", "L300", "Mirage"),
        "Lainnya" to emptyList()
    )

    // Launcher Galeri (Pilih Banyak Foto)
    private val pickMultipleImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            checkImagesRecursively(uris.toMutableList(), 0)
        }
    }

    // Launcher Kamera (Satu Foto)
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val uri = getImageUriFromBitmap(bitmap)
            if (uri != null) {
                checkSingleImage(uri)
            }
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) takePhoto.launch(null) else Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) pickMultipleImages.launch("image/*") else Toast.makeText(this, "Izin galeri ditolak", Toast.LENGTH_SHORT).show()
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) getCurrentLocation() else Toast.makeText(this, "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Inisialisasi Helper AI
        priceModelHelper = CarPriceModelHelper(this)
        imageClassifierHelper = ImageClassifierHelper(this, {}, {_,_ ->}) // Dummy init

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        progressDialog = ProgressDialog(this).apply {
            setCancelable(false)
            setMessage("Loading...")
        }
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
            binding.toolbarSellCar.title = "Jual Mobil"
            binding.btnSubmit.text = "Submit & Post"
            loadUserData()
        }

        binding.etPrice.addTextChangedListener(NumberTextWatcher(binding.etPrice))
        binding.etMileage.addTextChangedListener(NumberTextWatcher(binding.etMileage, useCurrency = false))
        binding.etCapacity.addTextChangedListener(NumberTextWatcher(binding.etCapacity, useCurrency = false))

        binding.btnGallery.setOnClickListener { checkStoragePermission() }
        binding.btnCamera.setOnClickListener { checkCameraPermission() }

        binding.btnUseMyLocation.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // --- PREDIKSI HARGA ---
        binding.btnGeneratePrice.setOnClickListener {
            val brand = getFinalBrand()
            val yearStr = binding.etYear.text.toString().trim()
            val mileageStr = NumberTextWatcher.cleanDigits(binding.etMileage.text.toString())
            val capacityStr = NumberTextWatcher.cleanDigits(binding.etCapacity.text.toString())

            if (brand.isBlank() || yearStr.isBlank()) {
                Toast.makeText(this, "Mohon isi Merek dan Tahun dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                binding.tvGeneratedPrice.text = "Menghitung..."
                val year = yearStr.toIntOrNull() ?: 2020
                val mileage = mileageStr.toIntOrNull() ?: 0
                val capacity = capacityStr.toIntOrNull() ?: 1500
                val predictedPrice = priceModelHelper.predict(brand, year, mileage, capacity)
                val formattedPrice = NumberTextWatcher.formatToRupiah(predictedPrice.toLong())
                binding.etPrice.setText(predictedPrice.toLong().toString())
                binding.tvGeneratedPrice.text = "Harga saran AI: $formattedPrice"
                binding.tvPriceWarning.text = "Prediksi Offline (TFLite)"
                binding.tvPriceWarning.setTextColor(ContextCompat.getColor(this, R.color.green))
                Toast.makeText(this, "Prediksi Selesai!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.tvGeneratedPrice.text = "Gagal"
            }
        }

        // --- TOMBOL SUBMIT ---
        binding.btnSubmit.setOnClickListener {
            if (validateInputs()) {
                showConfirmationDialog()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        priceModelHelper.close()
    }

    // --- FUNGSI YG DITAMBAHKAN: getImageUriFromBitmap ---
    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri? {
        return try {
            val bytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
            val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "OtoMatch_${System.currentTimeMillis()}", null)
            Uri.parse(path)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- LOGIKA CEK GAMBAR (SAAT PILIH FOTO) ---
    private fun checkSingleImage(uri: Uri) {
        progressDialog.setMessage("Mengecek gambar...")
        progressDialog.show()

        val bitmap = uriToBitmap(uri)
        if (bitmap != null) {
            imageClassifierHelper = ImageClassifierHelper(this, {
                progressDialog.dismiss()
                Toast.makeText(this, "Gagal deteksi: $it", Toast.LENGTH_SHORT).show()
            }, { isCar, objectName ->
                progressDialog.dismiss()
                if (isCar) {
                    addPhotoToList(uri)
                } else {
                    showRejectionDialog(objectName, uri)
                }
            })
            imageClassifierHelper.classify(bitmap)
        } else {
            progressDialog.dismiss()
        }
    }

    private fun checkImagesRecursively(uris: MutableList<Uri>, index: Int) {
        if (index >= uris.size) {
            progressDialog.dismiss()
            updateImagesPreview()
            return
        }

        if (index == 0) {
            progressDialog.setMessage("Mengecek ${uris.size} foto...")
            progressDialog.show()
        }

        val uri = uris[index]
        val bitmap = uriToBitmap(uri)

        if (bitmap != null) {
            imageClassifierHelper = ImageClassifierHelper(this, {
                checkImagesRecursively(uris, index + 1)
            }, { isCar, objectName ->
                if (isCar) {
                    selectedImageUris.add(uri)
                    displayImages.add(uri)
                    checkImagesRecursively(uris, index + 1)
                } else {
                    progressDialog.dismiss()
                    showRejectionDialogForMultiple(objectName, uri, uris, index)
                }
            })
            imageClassifierHelper.classify(bitmap)
        } else {
            checkImagesRecursively(uris, index + 1)
        }
    }

    private fun addPhotoToList(uri: Uri) {
        selectedImageUris.add(uri)
        displayImages.add(uri)
        updateImagesPreview()
        Toast.makeText(this, "Foto mobil ditambahkan", Toast.LENGTH_SHORT).show()
    }

    // --- DIALOG USER FRIENDLY (TANPA PERSEN) ---
    private fun showRejectionDialog(objectName: String, uri: Uri) {
        val msg = "Sistem mendeteksi ini gambar: **$objectName**.\nMohon upload foto mobil."
        AlertDialog.Builder(this)
            .setTitle("Bukan Mobil?")
            .setMessage(msg)
            .setPositiveButton("Ganti Foto") { _, _ -> /* User batal */ }
            .setNegativeButton("Tetap Upload (Paksa)") { _, _ ->
                addPhotoToList(uri)
            }
            .setCancelable(false)
            .show()
    }

    private fun showRejectionDialogForMultiple(objectName: String, currentUri: Uri, allUris: MutableList<Uri>, currentIndex: Int) {
        val msg = "Foto ke-${currentIndex+1} terdeteksi sebagai: **$objectName**.\nMohon upload foto mobil."
        AlertDialog.Builder(this)
            .setTitle("Foto Bermasalah")
            .setMessage(msg)
            .setPositiveButton("Lewati Foto Ini") { _, _ ->
                progressDialog.show()
                checkImagesRecursively(allUris, currentIndex + 1)
            }
            .setNegativeButton("Tetap Upload") { _, _ ->
                selectedImageUris.add(currentUri)
                displayImages.add(currentUri)
                progressDialog.show()
                checkImagesRecursively(allUris, currentIndex + 1)
            }
            .setCancelable(false)
            .show()
    }

    private fun updateImagesPreview() {
        displayImages.clear()
        // Masukkan foto lama (String)
        displayImages.addAll(existingImageUrls)
        // Masukkan foto baru (Uri)
        displayImages.addAll(selectedImageUris)

        // Callback saat Long Click (Hapus Foto)
        val adapter = ImageSliderAdapter(displayImages) { position ->
            showDeletePhotoDialog(position)
        }
        binding.rvSelectedImages.adapter = adapter

        if (displayImages.isNotEmpty()) {
            binding.tvPhotoCount.text = "${displayImages.size} Foto (Tekan lama untuk hapus)"
            binding.tvPhotoCount.visibility = View.VISIBLE
        } else {
            binding.tvPhotoCount.visibility = View.GONE
        }
    }

    private fun showDeletePhotoDialog(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Foto?")
            .setMessage("Ingin menghapus foto ini?")
            .setPositiveButton("Hapus") { _, _ -> removeItem(position) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun removeItem(position: Int) {
        val numExisting = existingImageUrls.size
        if (position < numExisting) {
            existingImageUrls.removeAt(position)
        } else {
            selectedImageUris.removeAt(position - numExisting)
        }
        updateImagesPreview()
        Toast.makeText(this, "Foto dihapus", Toast.LENGTH_SHORT).show()
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    private fun showConfirmationDialog() {
        val title = if (editingCarId != null) "Simpan Perubahan?" else "Posting Mobil?"
        AlertDialog.Builder(this).setTitle(title).setMessage("Yakin data sudah benar?")
            .setPositiveButton("Ya") { _, _ ->
                if (selectedImageUris.isNotEmpty()) {
                    uploadedImageUrls.clear()
                    uploadImagesRecursive(0)
                } else {
                    saveData(existingImageUrls)
                }
            }
            .setNegativeButton("Batal", null).show()
    }

    // --- FUNGSI UTILS STANDARD ---
    private fun loadUserData() {
        auth.currentUser?.let { user ->
            db.collection("users").document(user.uid).get().addOnSuccessListener { document ->
                if (document.exists()) {
                    binding.etSellerName.setText(document.getString("name"))
                    binding.etSellerContact.setText(document.getString("phone"))
                    val profileLocation = document.getString("location")
                    if (!profileLocation.isNullOrEmpty()) binding.actLocation.setText(profileLocation)
                }
            }
        }
    }

    private fun getCurrentLocation() {
        try {
            Toast.makeText(this, "Mencari lokasi...", Toast.LENGTH_SHORT).show()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        try {
                            val geocoder = Geocoder(this, Locale.getDefault())
                            @Suppress("DEPRECATION") val addresses =
                                geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val address = addresses[0]
                                val city = address.locality ?: address.subAdminArea
                                val state = address.adminArea
                                binding.actLocation.setText(listOfNotNull(city, state).joinToString(", "))
                            }
                        } catch (e: Exception) { Log.e("Loc", "Geo failed", e) }
                    }
                }
        } catch (e: SecurityException) {}
    }

    private fun uploadImagesRecursive(index: Int) {
        if (index >= selectedImageUris.size) {
            val finalUrls = mutableListOf<String>()
            finalUrls.addAll(existingImageUrls)
            finalUrls.addAll(uploadedImageUrls)
            saveData(finalUrls)
            return
        }
        progressDialog.setMessage("Upload foto ${index + 1}...")
        progressDialog.show()
        MediaManager.get().upload(selectedImageUris[index]).unsigned(UPLOAD_PRESET)
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
                    progressDialog.dismiss()
                    Toast.makeText(this@SellCarActivity, "Gagal upload", Toast.LENGTH_SHORT).show()
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            }).dispatch()
    }

    private fun saveData(finalImageUrls: List<String>) {
        progressDialog.setMessage("Menyimpan...")
        val priceString = NumberTextWatcher.cleanDigits(binding.etPrice.text.toString())
        val mileageString = NumberTextWatcher.cleanDigits(binding.etMileage.text.toString())
        val capacityString = NumberTextWatcher.cleanDigits(binding.etCapacity.text.toString())
        val colorCategory = binding.actColorCategory.text.toString()
        var colorSpecific = binding.etExactColor.text.toString()
        if (colorSpecific.isBlank()) colorSpecific = colorCategory
        val finalBrand = getFinalBrand()
        val finalModel = getFinalModel()

        val carDataMap = hashMapOf<String, Any>(
            "name" to "$finalBrand $finalModel", "brand" to finalBrand, "model" to finalModel,
            "year" to (binding.etYear.text.toString().toIntOrNull() ?: 2020),
            "price" to NumberTextWatcher.formatToRupiah(priceString.toLongOrNull() ?: 0),
            "mileage" to NumberTextWatcher.formatToKm(mileageString.toLongOrNull() ?: 0),
            "location" to binding.actLocation.text.toString(), "color" to colorCategory,
            "exactColor" to colorSpecific, "imageUrls" to finalImageUrls,
            "imageUrl" to (finalImageUrls.firstOrNull() ?: ""),
            "sellerName" to binding.etSellerName.text.toString(),
            "sellerContact" to binding.etSellerContact.text.toString(),
            "sellerType" to binding.actSellerType.text.toString(),
            "fuel" to binding.actFuelType.text.toString(),
            "transmission" to binding.actTransmission.text.toString(),
            "bodyType" to binding.actBodyType.text.toString(),
            "capacity" to if (capacityString.isEmpty()) "" else NumberTextWatcher.formatToCc(capacityString.toLongOrNull() ?: 0),
            "variant" to binding.etVariant.text.toString(),
            "negatives" to binding.etNegatives.text.toString(),
            "mods" to binding.etMods.text.toString()
        )

        if (editingCarId != null) {
            db.collection("cars").document(editingCarId!!).update(carDataMap).addOnSuccessListener {
                progressDialog.dismiss()
                setResult(Activity.RESULT_OK); finish()
            }
        } else {
            carDataMap["isSold"] = false
            carDataMap["sellerUid"] = auth.currentUser!!.uid
            carDataMap["createdAt"] = FieldValue.serverTimestamp()
            db.collection("cars").add(carDataMap).addOnSuccessListener {
                progressDialog.dismiss()
                setResult(Activity.RESULT_OK); finish()
            }
        }
    }

    private fun loadCarDataToEdit(carId: String) {
        db.collection("cars").document(carId).get().addOnSuccessListener { document ->
            val car = document.toObject(Car::class.java) ?: return@addOnSuccessListener
            binding.etSellerName.setText(car.sellerName)
            binding.etSellerContact.setText(car.sellerContact)
            binding.actSellerType.setText(car.sellerType, false)
            if (carDataMap.containsKey(car.brand)) {
                binding.actBrand.setText(car.brand, false)
                binding.actModel.setText(car.model, false)
                binding.actModel.isEnabled = true
            } else {
                binding.actBrand.setText("Lainnya", false)
                binding.tilManualBrand.visibility = View.VISIBLE
                binding.etManualBrand.setText(car.brand)
                binding.tilModel.visibility = View.GONE
                binding.tilManualModel.visibility = View.VISIBLE
                binding.etManualModel.setText(car.model)
            }
            binding.etYear.setText(car.year.toString())
            binding.etMileage.setText(NumberTextWatcher.cleanDigits(car.mileage))
            binding.actLocation.setText(car.location)
            binding.actColorCategory.setText(car.color, false)
            binding.etExactColor.setText(car.exactColor)
            binding.etVariant.setText(car.variant)
            binding.actFuelType.setText(car.fuel, false)
            binding.actTransmission.setText(car.transmission, false)
            binding.actBodyType.setText(car.bodyType, false)
            binding.etCapacity.setText(NumberTextWatcher.cleanDigits(car.capacity))
            binding.etNegatives.setText(car.negatives)
            binding.etMods.setText(car.mods)
            binding.etPrice.setText(NumberTextWatcher.cleanDigits(car.price))
            existingImageUrls.clear()
            if (car.imageUrls.isNotEmpty()) existingImageUrls.addAll(car.imageUrls)
            else if (car.imageUrl.isNotEmpty()) existingImageUrls.add(car.imageUrl)
            updateImagesPreview()
        }
    }

    private fun validateInputs(): Boolean {
        if (selectedImageUris.isEmpty() && existingImageUrls.isEmpty()) {
            Toast.makeText(this, "Wajib upload minimal 1 foto!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.actBrand.text.isBlank() || binding.etYear.text.isNullOrBlank() || binding.etPrice.text.isNullOrBlank()) {
            Toast.makeText(this, "Mohon lengkapi data utama", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto.launch(null)
        else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun checkStoragePermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) pickMultipleImages.launch("image/*")
        else requestStoragePermissionLauncher.launch(perm)
    }

    private fun setupDropdowns() {
        val brands = carDataMap.keys.toList().sorted().toMutableList()
        brands.remove("Lainnya")
        brands.add("Lainnya")
        binding.actBrand.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_custom, brands))
        binding.actBrand.setOnClickListener { binding.actBrand.showDropDown() }
        binding.actBrand.setOnItemClickListener { parent, _, position, _ ->
            val selectedBrand = parent.getItemAtPosition(position).toString()
            binding.actModel.text = null
            if (selectedBrand == "Lainnya") {
                binding.tilManualBrand.visibility = View.VISIBLE
                binding.tilModel.visibility = View.GONE
                binding.tilManualModel.visibility = View.VISIBLE
                binding.actModel.isEnabled = false
            } else {
                binding.tilManualBrand.visibility = View.GONE
                binding.tilModel.visibility = View.VISIBLE
                binding.tilManualModel.visibility = View.GONE
                val models = carDataMap[selectedBrand]?.sorted()?.toMutableList() ?: mutableListOf()
                models.add("Lainnya")
                binding.actModel.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_custom, models))
                binding.actModel.isEnabled = true
            }
        }
        binding.actModel.setOnClickListener { if (binding.actModel.isEnabled) binding.actModel.showDropDown() }

        binding.actLocation.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_custom, cityList))

        val types = arrayOf("Individu", "Diler")
        val fuels = arrayOf("Bensin", "Diesel", "Listrik", "Hybrid")
        val trans = arrayOf("Manual", "Automatic", "CVT")
        val bodies = arrayOf("SUV", "MPV", "Sedan", "Hatchback", "Coupe", "Van", "Pickup")
        val colors = arrayOf("Hitam", "Putih", "Silver", "Abu-abu", "Merah", "Biru", "Hijau", "Kuning", "Coklat", "Oranye", "Gold", "Ungu", "Lainnya")

        binding.actSellerType.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_custom, types))
        binding.actFuelType.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_custom, fuels))
        binding.actTransmission.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_custom, trans))
        binding.actBodyType.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_custom, bodies))
        binding.actColorCategory.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_custom, colors))

        listOf(binding.actSellerType, binding.actFuelType, binding.actTransmission, binding.actBodyType, binding.actColorCategory).forEach {
            it.setOnClickListener { v -> (v as? android.widget.AutoCompleteTextView)?.showDropDown() }
        }
    }

    private fun getFinalBrand(): String {
        val dropdown = binding.actBrand.text.toString()
        return if (dropdown == "Lainnya") binding.etManualBrand.text.toString().trim() else dropdown
    }

    private fun getFinalModel(): String {
        if (binding.actBrand.text.toString() == "Lainnya") return binding.etManualModel.text.toString().trim()
        val dropdown = binding.actModel.text.toString()
        return if (dropdown == "Lainnya") binding.etManualModel.text.toString().trim() else dropdown
    }
}