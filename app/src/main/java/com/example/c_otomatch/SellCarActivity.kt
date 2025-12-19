package com.example.c_otomatch

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Locale

class SellCarActivity : AppCompatActivity() {

    private val UPLOAD_PRESET = "OtoMatch_Preset"
    private lateinit var binding: ActivityAddCarBinding

    // Foto Utama (List)
    private var displayImages = mutableListOf<Any>()
    private var selectedImageUris = mutableListOf<Uri>()
    private var existingImageUrls = mutableListOf<String>()
    private var uploadedImageUrls = mutableListOf<String>()

    // Mode Foto (0=Main, 1=Minus, 2=Mod)
    private var currentPhotoMode = 0
    private val MODE_MAIN = 0
    private val MODE_MINUS = 1
    private val MODE_MOD = 2

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialog
    private var editingCarId: String? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var priceModelHelper: CarPriceModelHelper
    private lateinit var imageClassifierHelper: ImageClassifierHelper

    private var isListingSuspicious = false
    private var cityList = mutableListOf<String>()
    private var carDataMap = mutableMapOf<String, List<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        priceModelHelper = CarPriceModelHelper(this)
        imageClassifierHelper = ImageClassifierHelper(this, {}, { _, _ -> })

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
        setupInlineYearPicker() // LOGIKA BARU ROLLING & AUTO-CLOSE
        loadMetadataFromFirestore()

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

        setupTextWatchers()
        setupEvidenceWatchers() // LOGIKA BUKTI MINUS/MODIF
        setupButtons()
    }

    // ================== LOGIKA INLINE ROLLING + AUTO CLOSE ==================
    private fun setupInlineYearPicker() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val minYear = 1990

        binding.npYear.minValue = minYear
        binding.npYear.maxValue = currentYear + 1
        binding.npYear.value = currentYear
        binding.npYear.wrapSelectorWheel = true

        // Set Text awal
        val existingText = binding.etYear.text?.toString() ?: ""
        if (existingText.isNotEmpty()) {
            binding.npYear.value = existingText.toIntOrNull() ?: currentYear
        } else {
            binding.etYear.setText(currentYear.toString())
        }

        // Listener: Ubah text saat diputar
        binding.npYear.setOnValueChangedListener { _, _, newVal ->
            binding.etYear.setText(newVal.toString())
        }

        // Logic Buka/Tutup (Manual Klik)
        binding.etYear.setOnClickListener {
            toggleYearPicker(!binding.npYear.isShown)
        }

        // --- AUTO CLOSE LOGIC ---
        // Daftar semua view lain yang bisa di-klik / focus
        val otherFocusableViews = listOf(
            binding.etMileage,
            binding.etPrice,
            binding.etSellerName,
            binding.etSellerContact,
            binding.actSellerType,
            binding.actBrand,
            binding.actModel,
            binding.etManualBrand,
            binding.etManualModel,
            binding.actLocation,
            binding.actColorCategory,
            binding.etExactColor,
            binding.etVariant,
            binding.etCapacity,
            binding.etNegatives,
            binding.etMods
        )

        // Pasang listener ke semua view itu
        otherFocusableViews.forEach { view ->
            view.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && binding.npYear.visibility == View.VISIBLE) {
                    toggleYearPicker(false) // Tutup paksa
                }
            }
            // Tambahan untuk view tipe dropdown (AutoCompleteTextView) yang mungkin butuh OnClick juga
            view.setOnClickListener {
                if (binding.npYear.visibility == View.VISIBLE) {
                    toggleYearPicker(false)
                }
                // Teruskan fungsi asli dropdown (showDropDown)
                if (view is AutoCompleteTextView && !view.isPopupShowing) {
                    view.showDropDown()
                }
            }
        }
    }

    private fun toggleYearPicker(show: Boolean) {
        if (show) {
            binding.npYear.visibility = View.VISIBLE
            binding.etYear.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_back, 0) // Rotated icon ideally
            // Hapus fokus dari view lain agar keyboard turun (opsional)
            currentFocus?.clearFocus()
        } else {
            binding.npYear.visibility = View.GONE
            binding.etYear.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_sort, 0)
        }
    }

    // ================== LOGIKA EVIDENCE (MINUS/MOD) ==================
    private fun setupEvidenceWatchers() {
        // Minus Watcher
        binding.etNegatives.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                binding.layoutMinusEvidence.visibility = if (hasText) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Mod Watcher
        binding.etMods.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                binding.layoutModEvidence.visibility = if (hasText) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Click Listeners untuk Tombol Bukti
        binding.imgMinusEvidence.setOnClickListener {
            currentPhotoMode = MODE_MINUS
            checkStoragePermission()
        }

        binding.imgModEvidence.setOnClickListener {
            currentPhotoMode = MODE_MOD
            checkStoragePermission()
        }
    }

    // ================== LOGIKA SUBMIT & VALIDASI ==================
    private fun onSubmitClicked() {
        if (!validateInputs()) return
        showConfirmationDialog()
    }

    private fun proceedToUpload() {
        if (selectedImageUris.isNotEmpty()) {
            uploadedImageUrls.clear()
            uploadImagesRecursive(0)
        } else {
            saveData(existingImageUrls)
        }
    }

    private fun showConfirmationDialog() {
        val title = if (editingCarId != null) "Simpan Perubahan?" else "Posting Mobil?"
        AlertDialog.Builder(this).setTitle(title).setMessage("Pastikan semua data benar.")
            .setPositiveButton("Ya, Posting") { _, _ -> proceedToUpload() }
            .setNegativeButton("Batal", null).show()
    }

    private fun validateInputs(): Boolean {
        if (selectedImageUris.isEmpty() && existingImageUrls.isEmpty()) {
            Toast.makeText(this, "Wajib upload minimal 1 foto!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.actBrand.text.isNullOrBlank()) { binding.actBrand.error = "Pilih Merek"; return false }
        if (binding.actBrand.text.toString() == "Lainnya" && binding.etManualBrand.text.isNullOrBlank()) {
            binding.etManualBrand.error = "Isi Merek Manual"; return false
        }
        if (binding.etYear.text.isNullOrBlank()) { binding.etYear.error = "Pilih Tahun"; return false }
        if (binding.etPrice.text.isNullOrBlank()) { binding.etPrice.error = "Isi Harga"; return false }

        if (binding.cgFuel.checkedChipId == View.NO_ID) {
            Toast.makeText(this, "Pilih Bahan Bakar", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    // ================== DATA HANDLING ==================
    private fun addPhotoToList(uri: Uri) {
        selectedImageUris.add(uri)
        displayImages.add(uri)
        updateImagesPreview()

        if (currentPhotoMode == MODE_MINUS) {
            binding.imgMinusEvidence.setImageURI(uri)
            binding.imgMinusEvidence.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            binding.imgMinusEvidence.setPadding(0,0,0,0) // Hapus padding agar full
            Toast.makeText(this, "Bukti Minus Ditambahkan", Toast.LENGTH_SHORT).show()
        } else if (currentPhotoMode == MODE_MOD) {
            binding.imgModEvidence.setImageURI(uri)
            binding.imgModEvidence.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            binding.imgModEvidence.setPadding(0,0,0,0)
            Toast.makeText(this, "Bukti Modif Ditambahkan", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Foto utama ditambahkan", Toast.LENGTH_SHORT).show()
        }

        currentPhotoMode = MODE_MAIN
    }

    // ... (Sisa fungsi loadMetadata, loadUserData sama) ...

    private fun loadMetadataFromFirestore() {
        db.collection("otomatch_Data").document("locations").get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val cities = document.get("cities") as? List<String>
                    if (cities != null) {
                        cityList.clear(); cityList.addAll(cities)
                        val cityAdapter = ArrayAdapter(this, R.layout.item_dropdown_custom, cityList)
                        binding.actLocation.setAdapter(cityAdapter)
                    }
                }
            }
            .addOnFailureListener {
                cityList.add("Jakarta Pusat"); cityList.add("Tangerang")
            }

        db.collection("otomatch_Data").document("car_models").get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val brandsData = document.get("brands") as? Map<String, List<String>>
                    if (brandsData != null) {
                        carDataMap.clear(); carDataMap.putAll(brandsData)
                        val brands = carDataMap.keys.toList().sorted().toMutableList()
                        if (brands.contains("Lainnya")) { brands.remove("Lainnya"); brands.add("Lainnya") }
                        else { brands.add("Lainnya") }
                        val brandAdapter = ArrayAdapter(this, R.layout.item_dropdown_custom, brands)
                        binding.actBrand.setAdapter(brandAdapter)
                    }
                }
            }
    }

    private fun loadUserData() {
        auth.currentUser?.let { user ->
            db.collection("users").document(user.uid).get().addOnSuccessListener { document ->
                if (document.exists()) {
                    binding.etSellerName.setText(document.getString("name"))
                    binding.etSellerContact.setText(document.getString("phone"))
                    val loc = document.getString("location")
                    if (!loc.isNullOrEmpty()) binding.actLocation.setText(loc)
                }
            }
        }
    }

    private fun loadCarDataToEdit(carId: String) {
        db.collection("cars").document(carId).get().addOnSuccessListener { document ->
            val car = document.toObject(Car::class.java) ?: return@addOnSuccessListener

            binding.etSellerName.setText(car.sellerName)
            binding.etSellerContact.setText(car.sellerContact)
            binding.actSellerType.setText(car.sellerType, false)
            binding.etYear.setText(car.year.toString())

            if (car.year >= binding.npYear.minValue && car.year <= binding.npYear.maxValue) {
                binding.npYear.value = car.year
            }

            binding.etMileage.setText(NumberTextWatcher.cleanDigits(car.mileage))
            binding.actLocation.setText(car.location)
            binding.actColorCategory.setText(car.color, false)
            binding.etExactColor.setText(car.exactColor)
            binding.etVariant.setText(car.variant)
            binding.etCapacity.setText(NumberTextWatcher.cleanDigits(car.capacity))
            binding.etNegatives.setText(car.negatives)
            binding.etMods.setText(car.mods)
            binding.etPrice.setText(NumberTextWatcher.cleanDigits(car.price))

            setChipSelection(binding.cgFuel, car.fuel)
            setChipSelection(binding.cgTransmission, car.transmission)
            setChipSelection(binding.cgBody, car.bodyType)

            if (carDataMap.containsKey(car.brand)) {
                binding.actBrand.setText(car.brand, false)
                val models = carDataMap[car.brand]?.sorted()?.toMutableList() ?: mutableListOf()
                models.add("Lainnya")
                binding.actModel.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_custom, models))
                binding.actModel.setText(car.model, false)
                binding.actModel.isEnabled = true
                binding.tilManualBrand.visibility = View.GONE
                binding.tilModel.visibility = View.VISIBLE
                binding.tilManualModel.visibility = View.GONE
            } else {
                binding.actBrand.setText("Lainnya", false)
                binding.tilManualBrand.visibility = View.VISIBLE
                binding.etManualBrand.setText(car.brand)
                binding.tilModel.visibility = View.GONE
                binding.tilManualModel.visibility = View.GONE
                binding.etManualModel.setText(car.model)
                binding.actModel.isEnabled = false
            }

            existingImageUrls.clear()
            if (car.imageUrls.isNotEmpty()) existingImageUrls.addAll(car.imageUrls)
            else if (car.imageUrl.isNotEmpty()) existingImageUrls.add(car.imageUrl)
            updateImagesPreview()
        }
    }

    private fun setupTextWatchers() {
        binding.etPrice.addTextChangedListener(NumberTextWatcher(binding.etPrice))
        binding.etMileage.addTextChangedListener(NumberTextWatcher(binding.etMileage, useCurrency = false))
        binding.etCapacity.addTextChangedListener(NumberTextWatcher(binding.etCapacity, useCurrency = false))
    }

    private fun setupButtons() {
        binding.btnGallery.setOnClickListener {
            currentPhotoMode = MODE_MAIN // Default
            checkStoragePermission()
        }
        binding.btnCamera.setOnClickListener {
            currentPhotoMode = MODE_MAIN // Default
            checkCameraPermission()
        }
        binding.btnUseMyLocation.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        binding.llPhotoPlaceholder.setOnClickListener {
            currentPhotoMode = MODE_MAIN
            checkStoragePermission()
        }

        binding.btnGeneratePrice.setOnClickListener { generatePrice() }
        binding.btnSubmit.setOnClickListener { onSubmitClicked() }
    }

    private fun generatePrice() {
        val brand = getFinalBrand()
        val yearStr = binding.etYear.text?.toString()?.trim() ?: ""
        val mileageStr = NumberTextWatcher.cleanDigits(binding.etMileage.text?.toString() ?: "")
        val capacityStr = NumberTextWatcher.cleanDigits(binding.etCapacity.text?.toString() ?: "")
        val fuel = getSelectedChipText(binding.cgFuel)
        val trans = getSelectedChipText(binding.cgTransmission)

        if (brand.isBlank() || yearStr.isBlank() || fuel.isBlank() || trans.isBlank()) {
            Toast.makeText(this, "Lengkapi Merek, Tahun, BBM, Transmisi", Toast.LENGTH_SHORT).show()
            return
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
        } catch (e: Exception) {
            binding.tvGeneratedPrice.text = "Gagal"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        priceModelHelper.close()
    }

    private fun getSelectedChipText(chipGroup: ChipGroup): String {
        val chipId = chipGroup.checkedChipId
        return if (chipId != View.NO_ID) {
            chipGroup.findViewById<Chip>(chipId)?.text.toString()
        } else ""
    }

    private fun setChipSelection(chipGroup: ChipGroup, text: String) {
        for (view in chipGroup.children) {
            if (view is Chip && view.text.toString().equals(text, ignoreCase = true)) {
                view.isChecked = true
                return
            }
        }
    }

    private fun setupDropdowns() {
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
                val modelAdapter = ArrayAdapter(this, R.layout.item_dropdown_custom, models)
                binding.actModel.setAdapter(modelAdapter)
                binding.actModel.isEnabled = true
            }
        }
        binding.actModel.setOnClickListener { if (binding.actModel.isEnabled) binding.actModel.showDropDown() }

        val types = arrayOf("Individu", "Diler")
        val colors = arrayOf("Hitam", "Putih", "Silver", "Abu-abu", "Merah", "Biru", "Hijau", "Kuning", "Coklat", "Oranye", "Gold", "Ungu", "Lainnya")

        binding.actSellerType.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_custom, types))
        binding.actColorCategory.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_custom, colors))

        listOf(binding.actSellerType, binding.actColorCategory).forEach {
            it.setOnClickListener { v -> (v as? android.widget.AutoCompleteTextView)?.showDropDown() }
        }
    }

    private val pickMultipleImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) checkImagesRecursively(uris.toMutableList(), 0)
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val uri = getImageUriFromBitmap(bitmap)
            if (uri != null) checkSingleImage(uri)
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) takePhoto.launch(null) else Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
    }
    private val requestStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) pickMultipleImages.launch("image/*") else Toast.makeText(this, "Izin galeri ditolak", Toast.LENGTH_SHORT).show()
    }
    private val requestLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) getCurrentLocation() else Toast.makeText(this, "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
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

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri? {
        return try {
            val bytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
            val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "OtoMatch_${System.currentTimeMillis()}", null)
            Uri.parse(path)
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    private fun checkSingleImage(uri: Uri) {
        progressDialog.setMessage("Mengecek gambar...")
        progressDialog.show()
        val bitmap = uriToBitmap(uri)
        if (bitmap != null) {
            imageClassifierHelper = ImageClassifierHelper(this, { progressDialog.dismiss() }, { isCar, objectName ->
                progressDialog.dismiss()
                if (isCar) addPhotoToList(uri) else showRejectionDialog(objectName, uri)
            })
            imageClassifierHelper.classify(bitmap)
        } else progressDialog.dismiss()
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
            imageClassifierHelper = ImageClassifierHelper(this, { checkImagesRecursively(uris, index + 1) }, { isCar, objectName ->
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
        } else checkImagesRecursively(uris, index + 1)
    }

    private fun showRejectionDialog(objectName: String, uri: Uri) {
        AlertDialog.Builder(this).setTitle("Bukan Mobil?").setMessage("Terdeteksi: $objectName.\nMohon upload foto mobil.")
            .setPositiveButton("Ganti Foto", null)
            .setNegativeButton("Tetap Upload") { _, _ -> showDoubleConfirmationDialog(uri) }
            .setCancelable(false).show()
    }

    private fun showDoubleConfirmationDialog(uri: Uri) {
        AlertDialog.Builder(this).setTitle("Konfirmasi Ulang").setMessage("Anda yakin foto ini mobil?")
            .setPositiveButton("Ya, Saya Yakin") { _, _ ->
                isListingSuspicious = true
                addPhotoToList(uri)
            }
            .setNegativeButton("Batal") { _, _ -> }
            .setCancelable(false).show()
    }

    private fun showRejectionDialogForMultiple(objectName: String, currentUri: Uri, allUris: MutableList<Uri>, currentIndex: Int) {
        AlertDialog.Builder(this).setTitle("Foto Bermasalah").setMessage("Foto ke-${currentIndex+1} terdeteksi: $objectName")
            .setPositiveButton("Lewati") { _, _ -> progressDialog.show(); checkImagesRecursively(allUris, currentIndex + 1) }
            .setNegativeButton("Tetap Upload") { _, _ -> showDoubleConfirmationDialogForMultiple(currentUri, allUris, currentIndex) }
            .setCancelable(false).show()
    }

    private fun showDoubleConfirmationDialogForMultiple(currentUri: Uri, allUris: MutableList<Uri>, currentIndex: Int) {
        AlertDialog.Builder(this).setTitle("Konfirmasi").setMessage("Yakin foto ke-${currentIndex+1} adalah mobil?")
            .setPositiveButton("Ya") { _, _ ->
                isListingSuspicious = true
                selectedImageUris.add(currentUri)
                displayImages.add(currentUri)
                progressDialog.show()
                checkImagesRecursively(allUris, currentIndex + 1)
            }
            .setNegativeButton("Batal Foto Ini") { _, _ ->
                progressDialog.show()
                checkImagesRecursively(allUris, currentIndex + 1)
            }.setCancelable(false).show()
    }

    private fun updateImagesPreview() {
        displayImages.clear()
        displayImages.addAll(existingImageUrls)
        displayImages.addAll(selectedImageUris)

        val adapter = ImageSliderAdapter(displayImages) { position -> showDeletePhotoDialog(position) }
        binding.rvSelectedImages.adapter = adapter

        val hasImages = displayImages.isNotEmpty()

        // Update Text Jumlah Foto
        binding.tvPhotoCount.visibility = if (hasImages) View.VISIBLE else View.GONE
        binding.tvPhotoCount.text = "${displayImages.size} Foto"

        // LOGIKA PLACEHOLDER & RECYCLERVIEW
        if (hasImages) {
            // Jika ada foto: Tampilkan List, Sembunyikan Placeholder
            binding.rvSelectedImages.visibility = View.VISIBLE
            binding.llPhotoPlaceholder.visibility = View.GONE
        } else {
            // Jika KOSONG: Sembunyikan List (BIAR GAK NGALANGIN KLIK), Tampilkan Placeholder
            binding.rvSelectedImages.visibility = View.GONE
            binding.llPhotoPlaceholder.visibility = View.VISIBLE
        }
    }

    private fun showDeletePhotoDialog(position: Int) {
        AlertDialog.Builder(this).setTitle("Hapus?").setMessage("Ingin menghapus foto ini?")
            .setPositiveButton("Hapus") { _, _ -> removeItem(position) }
            .setNegativeButton("Batal", null).show()
    }

    private fun removeItem(position: Int) {
        val numExisting = existingImageUrls.size
        if (position < numExisting) existingImageUrls.removeAt(position)
        else selectedImageUris.removeAt(position - numExisting)
        updateImagesPreview()

        // Reset preview khusus jika foto dihapus (optional)
        if (displayImages.isEmpty()) {
            binding.imgMinusEvidence.setImageResource(R.drawable.ic_add)
            binding.imgMinusEvidence.setPadding(12,12,12,12)
            binding.imgModEvidence.setImageResource(R.drawable.ic_add)
            binding.imgModEvidence.setPadding(12,12,12,12)
        }
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
        val priceString = NumberTextWatcher.cleanDigits(binding.etPrice.text?.toString() ?: "")
        val mileageString = NumberTextWatcher.cleanDigits(binding.etMileage.text?.toString() ?: "")
        val capacityString = NumberTextWatcher.cleanDigits(binding.etCapacity.text?.toString() ?: "")
        val colorCategory = binding.actColorCategory.text?.toString() ?: ""
        var colorSpecific = binding.etExactColor.text?.toString() ?: ""
        if (colorSpecific.isBlank()) colorSpecific = colorCategory
        val finalBrand = getFinalBrand()
        val finalModel = getFinalModel()

        // GET VALUES FROM CHIPS
        val fuel = getSelectedChipText(binding.cgFuel)
        val trans = getSelectedChipText(binding.cgTransmission)
        val body = getSelectedChipText(binding.cgBody)

        val carDataMap = hashMapOf<String, Any>(
            "name" to "$finalBrand $finalModel",
            "brand" to finalBrand,
            "model" to finalModel,
            "year" to (binding.etYear.text?.toString()?.toIntOrNull() ?: 2020),
            "price" to NumberTextWatcher.formatToRupiah(priceString.toLongOrNull() ?: 0),
            "mileage" to NumberTextWatcher.formatToKm(mileageString.toLongOrNull() ?: 0),
            "location" to (binding.actLocation.text?.toString() ?: ""),
            "color" to colorCategory,
            "exactColor" to colorSpecific,
            "imageUrls" to finalImageUrls,
            "imageUrl" to (finalImageUrls.firstOrNull() ?: ""),
            "sellerName" to (binding.etSellerName.text?.toString() ?: ""),
            "sellerContact" to (binding.etSellerContact.text?.toString() ?: ""),
            "sellerType" to (binding.actSellerType.text?.toString() ?: ""),
            "fuel" to fuel,
            "transmission" to trans,
            "bodyType" to body,
            "capacity" to if (capacityString.isEmpty()) "" else NumberTextWatcher.formatToCc(capacityString.toLongOrNull() ?: 0),
            "variant" to (binding.etVariant.text?.toString() ?: ""),
            "negatives" to (binding.etNegatives.text?.toString() ?: ""),
            "mods" to (binding.etMods.text?.toString() ?: ""),
            "isSuspicious" to isListingSuspicious
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

    private fun getCurrentLocation() {
        try {
            Toast.makeText(this, "Mencari lokasi...", Toast.LENGTH_SHORT).show()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        try {
                            val geocoder = Geocoder(this, Locale.getDefault())
                            @Suppress("DEPRECATION") val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
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

    private fun getFinalBrand(): String {
        val dropdown = binding.actBrand.text?.toString() ?: ""
        return if (dropdown == "Lainnya") binding.etManualBrand.text?.toString()?.trim() ?: "" else dropdown
    }

    private fun getFinalModel(): String {
        if (binding.actBrand.text?.toString() == "Lainnya") return binding.etManualModel.text?.toString()?.trim() ?: ""
        val dropdown = binding.actModel.text?.toString() ?: ""
        return if (dropdown == "Lainnya") binding.etManualModel.text?.toString()?.trim() ?: "" else dropdown
    }
}