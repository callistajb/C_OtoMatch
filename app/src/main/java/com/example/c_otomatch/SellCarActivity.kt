package com.example.c_otomatch

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
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
import com.google.android.material.bottomsheet.BottomSheetDialog
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
        setupInlineYearPicker()
        setupTaxLogic()
        setupPlateLogic()
        loadMetadataFromFirestore()
        val allCapsFilter = android.text.InputFilter.AllCaps()
        binding.etPlatePrefix.filters = arrayOf(allCapsFilter, android.text.InputFilter.LengthFilter(2))
        binding.etPlateSuffix.filters = arrayOf(allCapsFilter, android.text.InputFilter.LengthFilter(3))

        binding.rvSelectedImages.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

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
        setupEvidenceWatchers()
        setupButtons()
    }

    private fun setupTaxLogic() {
        binding.tilTaxDate.hint = "Pilih Tanggal Pajak"

        binding.cgTax.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.chipTaxOn) {
                binding.tilTaxDate.hint = "Berlaku Sampai (Pilih Tanggal)"
            } else if (checkedId == R.id.chipTaxOff) {
                binding.tilTaxDate.hint = "Pajak Mati Sejak (Pilih Tanggal)"
            }
        }

        binding.etTaxDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog =
            DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                // Format hasil: "25 Agustus 2025" (Bahasa Indonesia)
                val cal = Calendar.getInstance()
                cal.set(selectedYear, selectedMonth, selectedDay)

                // Format tanggal yang human-readable
                val format = java.text.SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
                binding.etTaxDate.setText(format.format(cal.time))

            }, year, month, day)

        datePickerDialog.show()
    }

    // ================== LOGIKA PLAT NOMOR (AUTO DETECT) ==================
    private fun setupPlateLogic() {
        // Ketika angka plat berubah, otomatis set Chip Ganjil/Genap
        binding.etPlateNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) {
                    try {
                        val lastDigit = s.toString().takeLast(1).toInt()
                        if (lastDigit % 2 == 0) {
                            binding.chipGenap.isChecked = true
                        } else {
                            binding.chipGanjil.isChecked = true
                        }
                        // Non-aktifkan klik manual agar user merasa ini "Smart"
                        // Tapi kalau mau manual override, bisa ubah setEnabled(true)
                        binding.chipGanjil.isEnabled = false
                        binding.chipGenap.isEnabled = false
                    } catch (e: Exception) {
                    }
                } else {
                    binding.cgPlateType.clearCheck()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ================== LOGIKA INLINE YEAR (EXISTING) ==================
    private fun setupInlineYearPicker() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val minYear = 1990

        binding.npYear.minValue = minYear
        binding.npYear.maxValue = currentYear + 1
        binding.npYear.value = currentYear
        binding.npYear.wrapSelectorWheel = true

        val existingText = binding.etYear.text?.toString() ?: ""
        if (existingText.isNotEmpty()) {
            binding.npYear.value = existingText.toIntOrNull() ?: currentYear
        } else {
            binding.etYear.setText(currentYear.toString())
        }

        binding.npYear.setOnValueChangedListener { _, _, newVal ->
            binding.etYear.setText(newVal.toString())
        }

        binding.etYear.setOnClickListener {
            toggleYearPicker(!binding.npYear.isShown)
        }

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
            binding.etMods,
            binding.etTaxDate,
            binding.etPlatePrefix,
            binding.etPlateNumber,
            binding.etPlateSuffix
        )

        otherFocusableViews.forEach { view ->
            view.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && binding.npYear.visibility == View.VISIBLE) toggleYearPicker(false)
            }
            view.setOnClickListener {
                if (binding.npYear.visibility == View.VISIBLE) toggleYearPicker(false)
                if (view is AutoCompleteTextView && !view.isPopupShowing) view.showDropDown()
                if (view.id == R.id.etTaxDate) showDatePicker()
            }
        }
    }

    private fun toggleYearPicker(show: Boolean) {
        if (show) {
            binding.npYear.visibility = View.VISIBLE
            binding.etYear.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, R.drawable.ic_arrow_back, 0
            )
            currentFocus?.clearFocus()
        } else {
            binding.npYear.visibility = View.GONE
            binding.etYear.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_sort, 0)
        }
    }

    // ================== LOGIKA EVIDENCE (MINUS/MOD) ==================
    private fun setupEvidenceWatchers() {
        binding.etNegatives.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                binding.layoutMinusEvidence.visibility = if (hasText) View.VISIBLE else View.GONE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etMods.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                binding.layoutModEvidence.visibility = if (hasText) View.VISIBLE else View.GONE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.imgMinusEvidence.setOnClickListener {
            currentPhotoMode = MODE_MINUS
            showFuturisticPhotoPicker()
        }

        binding.imgModEvidence.setOnClickListener {
            currentPhotoMode = MODE_MOD
            showFuturisticPhotoPicker()
        }
    }

    // ================== SUBMIT & SAVE ==================
    private fun onSubmitClicked() {
        if (!validateInputs()) return

        val negatives = binding.etNegatives.text?.toString()?.trim() ?: ""
        val mods = binding.etMods.text?.toString()?.trim() ?: ""

        val totalPhotos = selectedImageUris.size + existingImageUrls.size
        if ((negatives.isNotEmpty() || mods.isNotEmpty()) && totalPhotos < 3) {
            AlertDialog.Builder(this).setTitle("Foto Kurang Lengkap")
                .setMessage("Anda mengisi detail Minus atau Modifikasi.\n\nMohon upload minimal 3 foto.")
                .setPositiveButton("Tambah Foto", null).show()
            return
        }

        if (negatives.isEmpty() && mods.isEmpty()) {
            showConditionCheckDialog()
        } else {
            showFinalConfirmDialog()
        }
    }

    private fun showConditionCheckDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_condition_check, null)
        val btnYes = view.findViewById<Button>(R.id.btnConfirmPerfect)
        val btnNo = view.findViewById<Button>(R.id.btnAddDetails)

        btnYes.setOnClickListener {
            dialog.dismiss()
            showFinalConfirmDialog()
        }
        btnNo.setOnClickListener {
            dialog.dismiss()
            binding.etNegatives.requestFocus()
            Toast.makeText(this, "Silakan isi detail minus/kerusakan", Toast.LENGTH_SHORT).show()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showFinalConfirmDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_final_confirm, null)
        val tvName = view.findViewById<TextView>(R.id.tvSummaryName)
        val tvYear = view.findViewById<TextView>(R.id.tvSummaryYear)
        val tvPrice = view.findViewById<TextView>(R.id.tvSummaryPrice)
        val btnPost = view.findViewById<Button>(R.id.btnFinalPost)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelPost)

        val finalBrand = getFinalBrand()
        val finalModel = getFinalModel()

        tvName.text = "$finalBrand $finalModel"
        tvYear.text = "Tahun ${binding.etYear.text}"
        tvPrice.text = binding.etPrice.text

        btnPost.setOnClickListener {
            dialog.dismiss()
            proceedToUpload()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun proceedToUpload() {
        if (selectedImageUris.isNotEmpty()) {
            uploadedImageUrls.clear()
            uploadImagesRecursive(0)
        } else {
            saveData(existingImageUrls)
        }
    }

    private fun validateInputs(): Boolean {
        if (selectedImageUris.isEmpty() && existingImageUrls.isEmpty()) {
            Toast.makeText(this, "Wajib upload minimal 1 foto!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.actBrand.text.isNullOrBlank()) {
            binding.actBrand.error = "Pilih Merek"; return false
        }
        if (binding.etYear.text.isNullOrBlank()) {
            binding.etYear.error = "Pilih Tahun"; return false
        }
        if (binding.etPrice.text.isNullOrBlank()) {
            binding.etPrice.error = "Isi Harga"; return false
        }

        // VALIDASI PAJAK
        if (binding.cgTax.checkedChipId == View.NO_ID) {
            Toast.makeText(this, "Pilih Status Pajak", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.etTaxDate.text.isNullOrBlank()) {
            Toast.makeText(this, "Isi tanggal pajak (Berlaku/Mati)", Toast.LENGTH_SHORT).show()
            return false
        }

        // VALIDASI PLAT NOMOR (Simple Check)
        if (binding.etPlatePrefix.text.isNullOrBlank() || binding.etPlateNumber.text.isNullOrBlank() || binding.etPlateSuffix.text.isNullOrBlank()) {
            Toast.makeText(this, "Lengkapi Plat Nomor", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    // ================== SAVE DATA ==================
    private fun saveData(finalImageUrls: List<String>) {
        progressDialog.setMessage("Menyimpan...")
        val priceString = NumberTextWatcher.cleanDigits(binding.etPrice.text?.toString() ?: "")
        val mileageString = NumberTextWatcher.cleanDigits(binding.etMileage.text?.toString() ?: "")
        val capacityString =
            NumberTextWatcher.cleanDigits(binding.etCapacity.text?.toString() ?: "")
        val colorCategory = binding.actColorCategory.text?.toString() ?: ""
        var colorSpecific = binding.etExactColor.text?.toString() ?: ""
        if (colorSpecific.isBlank()) colorSpecific = colorCategory
        val finalBrand = getFinalBrand()
        val finalModel = getFinalModel()

        val fuel = getSelectedChipText(binding.cgFuel)
        val trans = getSelectedChipText(binding.cgTransmission)
        val body = getSelectedChipText(binding.cgBody)

        // DATA PAJAK
        val taxStatus = getSelectedChipText(binding.cgTax)
        val taxDate = if (taxStatus == "Mati") binding.etTaxDate.text.toString() else ""

        // DATA PLAT NOMOR (Gabungkan String)
        val plate =
            "${binding.etPlatePrefix.text} ${binding.etPlateNumber.text} ${binding.etPlateSuffix.text}".uppercase()
        val plateType = getSelectedChipText(binding.cgPlateType)

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
            "capacity" to if (capacityString.isEmpty()) "" else NumberTextWatcher.formatToCc(
                capacityString.toLongOrNull() ?: 0
            ),
            "variant" to (binding.etVariant.text?.toString() ?: ""),
            "negatives" to (binding.etNegatives.text?.toString() ?: ""),
            "mods" to (binding.etMods.text?.toString() ?: ""),
            "taxStatus" to taxStatus,
            "taxDate" to taxDate,
            "plateNumber" to plate, // SIMPAN PLAT
            "plateType" to plateType, // SIMPAN TIPE PLAT
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

    // ... (SISA KODE LOAD METADATA, PERMISSIONS, UPLOAD FOTO TETAP SAMA KARENA SUDAH BENAR) ...
    // Saya sertakan helper singkat di bawah agar tidak error saat copy paste

    private fun loadMetadataFromFirestore() {
        db.collection("otomatch_Data").document("locations").get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val cities = document.get("cities") as? List<String>
                    if (cities != null) {
                        cityList.clear(); cityList.addAll(cities)
                        val cityAdapter =
                            ArrayAdapter(this, R.layout.item_dropdown_custom, cityList)
                        binding.actLocation.setAdapter(cityAdapter)
                    }
                }
            }
        db.collection("otomatch_Data").document("car_models").get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val brandsData = document.get("brands") as? Map<String, List<String>>
                    if (brandsData != null) {
                        carDataMap.clear(); carDataMap.putAll(brandsData)
                        val brands = carDataMap.keys.toList().sorted().toMutableList()
                        brands.add("Lainnya")
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
            if (car.year >= binding.npYear.minValue && car.year <= binding.npYear.maxValue) binding.npYear.value =
                car.year

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

            // LOAD PAJAK
            setChipSelection(binding.cgTax, car.taxStatus)
            if (car.taxStatus == "Mati") {
                binding.tilTaxDate.visibility = View.VISIBLE
                binding.etTaxDate.setText(car.taxDate)
            }

            // LOAD PLAT NOMOR (SPLIT STRING)
            if (car.plateNumber.isNotEmpty()) {
                val parts = car.plateNumber.split(" ")
                if (parts.size >= 3) {
                    binding.etPlatePrefix.setText(parts[0])
                    binding.etPlateNumber.setText(parts[1])
                    binding.etPlateSuffix.setText(parts[2])
                }
            }

            if (carDataMap.containsKey(car.brand)) {
                binding.actBrand.setText(car.brand, false)
                val models = carDataMap[car.brand]?.sorted()?.toMutableList() ?: mutableListOf()
                models.add("Lainnya")
                binding.actModel.setAdapter(
                    ArrayAdapter(
                        this, R.layout.item_dropdown_custom, models
                    )
                )
                binding.actModel.setText(car.model, false)
                binding.actModel.isEnabled = true
            } else {
                binding.actBrand.setText("Lainnya", false)
                binding.tilManualBrand.visibility = View.VISIBLE
                binding.etManualBrand.setText(car.brand)
                binding.etManualModel.setText(car.model)
            }

            existingImageUrls.clear()
            if (car.imageUrls.isNotEmpty()) existingImageUrls.addAll(car.imageUrls)
            else if (car.imageUrl.isNotEmpty()) existingImageUrls.add(car.imageUrl)
            updateImagesPreview()
        }
    }

    private fun setupTextWatchers() {
        binding.etPrice.addTextChangedListener(NumberTextWatcher(binding.etPrice))
        binding.etMileage.addTextChangedListener(
            NumberTextWatcher(
                binding.etMileage, useCurrency = false
            )
        )
        binding.etCapacity.addTextChangedListener(
            NumberTextWatcher(
                binding.etCapacity, useCurrency = false
            )
        )
    }

    private fun getSelectedChipText(chipGroup: ChipGroup): String {
        val chipId = chipGroup.checkedChipId
        return if (chipId != View.NO_ID) chipGroup.findViewById<Chip>(chipId)?.text.toString() else ""
    }

    private fun setChipSelection(chipGroup: ChipGroup, text: String) {
        for (view in chipGroup.children) {
            if (view is Chip && view.text.toString().equals(text, ignoreCase = true)) {
                view.isChecked = true; return
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
        val colors = arrayOf(
            "Hitam",
            "Putih",
            "Silver",
            "Abu-abu",
            "Merah",
            "Biru",
            "Hijau",
            "Kuning",
            "Coklat",
            "Oranye",
            "Gold",
            "Ungu",
            "Lainnya"
        )
        binding.actSellerType.setAdapter(ArrayAdapter(this, R.layout.item_dropdown_custom, types))
        binding.actColorCategory.setAdapter(
            ArrayAdapter(
                this, R.layout.item_dropdown_custom, colors
            )
        )
        listOf(
            binding.actSellerType, binding.actColorCategory
        ).forEach { it.setOnClickListener { v -> (v as? android.widget.AutoCompleteTextView)?.showDropDown() } }
    }

    private fun setupButtons() {
        binding.btnGallery.setOnClickListener {
            currentPhotoMode = MODE_MAIN; showFuturisticPhotoPicker()
        }
        binding.btnCamera.setOnClickListener {
            currentPhotoMode = MODE_MAIN; showFuturisticPhotoPicker()
        }
        binding.btnUseMyLocation.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) getCurrentLocation()
            else requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        binding.btnAddMorePhotos.setOnClickListener {
            currentPhotoMode = MODE_MAIN
            showFuturisticPhotoPicker()
        }
        binding.llPhotoPlaceholder.setOnClickListener {
            currentPhotoMode = MODE_MAIN
            showFuturisticPhotoPicker()
        }
        binding.btnGeneratePrice.setOnClickListener { generatePrice() }
        binding.btnSubmit.setOnClickListener { onSubmitClicked() }
    }

    private fun generatePrice() {
        val brand = getFinalBrand()
        val yearStr = binding.etYear.text?.toString()?.trim() ?: ""
        val mileageStr = NumberTextWatcher.cleanDigits(binding.etMileage.text?.toString() ?: "")
        val capacityStr = NumberTextWatcher.cleanDigits(binding.etCapacity.text?.toString() ?: "")
        if (brand.isBlank() || yearStr.isBlank()) {
            Toast.makeText(this, "Lengkapi Merek & Tahun", Toast.LENGTH_SHORT).show(); return
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
        } catch (e: Exception) {
            binding.tvGeneratedPrice.text = "Gagal"
        }
    }

    private fun getCurrentLocation() {
        try {
            Toast.makeText(this, "Mencari lokasi...", Toast.LENGTH_SHORT).show()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val geocoder = Geocoder(this, Locale.getDefault())
                        val addresses =
                            geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            binding.actLocation.setText(
                                listOfNotNull(
                                    address.locality, address.adminArea
                                ).joinToString(", ")
                            )
                        }
                    }
                }
        } catch (e: SecurityException) {
        }
    }

    private fun getFinalBrand(): String {
        val dropdown = binding.actBrand.text?.toString() ?: ""
        return if (dropdown == "Lainnya") binding.etManualBrand.text?.toString()?.trim()
            ?: "" else dropdown
    }

    private fun getFinalModel(): String {
        if (binding.actBrand.text?.toString() == "Lainnya") return binding.etManualModel.text?.toString()
            ?.trim() ?: ""
        val dropdown = binding.actModel.text?.toString() ?: ""
        return if (dropdown == "Lainnya") binding.etManualModel.text?.toString()?.trim()
            ?: "" else dropdown
    }

    // --- PHOTO PICKER & UPLOAD ---
    private fun showFuturisticPhotoPicker() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_photo_picker, null)
        view.findViewById<View>(R.id.btnBsCamera)
            .setOnClickListener { dialog.dismiss(); checkCameraPermission() }
        view.findViewById<View>(R.id.btnBsGallery)
            .setOnClickListener { dialog.dismiss(); checkStoragePermission() }
        dialog.setContentView(view); dialog.show()
    }

    private val pickMultipleImages =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) checkImagesRecursively(
                uris.toMutableList(), 0
            )
        }
    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let {
                checkSingleImage(getImageUriFromBitmap(it)!!)
            }
        }
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) takePhoto.launch(null)
        }
    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) pickMultipleImages.launch("image/*")
        }
    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) getCurrentLocation() }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) takePhoto.launch(null) else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) pickMultipleImages.launch("image/*") else requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(
            contentResolver, bitmap, "OtoMatch_${System.currentTimeMillis()}", null
        )
        return Uri.parse(path)
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
        } catch (e: Exception) {
            null
        }
    }

    private fun checkSingleImage(uri: Uri) {
        progressDialog.setMessage("Mengecek gambar..."); progressDialog.show()
        val bitmap = uriToBitmap(uri)
        if (bitmap != null) {
            imageClassifierHelper =
                ImageClassifierHelper(this, { progressDialog.dismiss() }, { isCar, _ ->
                    progressDialog.dismiss()
                    if (isCar) addPhotoToList(uri) else showRejectionDialog("Objek Lain", uri)
                })
            imageClassifierHelper.classify(bitmap)
        } else progressDialog.dismiss()
    }

    private fun checkImagesRecursively(uris: MutableList<Uri>, index: Int) {
        if (index >= uris.size) {
            progressDialog.dismiss(); updateImagesPreview(); return
        }
        if (index == 0) {
            progressDialog.setMessage("Mengecek foto..."); progressDialog.show()
        }
        val uri = uris[index]
        val bitmap = uriToBitmap(uri)
        if (bitmap != null) {
            imageClassifierHelper = ImageClassifierHelper(
                this,
                { checkImagesRecursively(uris, index + 1) },
                { isCar, _ ->
                    if (isCar) {
                        selectedImageUris.add(uri); displayImages.add(uri); checkImagesRecursively(
                            uris, index + 1
                        )
                    } else {
                        progressDialog.dismiss(); showRejectionDialogForMultiple(
                            "Bukan Mobil", uri, uris, index
                        )
                    }
                })
            imageClassifierHelper.classify(bitmap)
        } else checkImagesRecursively(uris, index + 1)
    }

    private fun addPhotoToList(uri: Uri) {
        selectedImageUris.add(uri)
        displayImages.add(uri)
        updateImagesPreview()

        if (currentPhotoMode == MODE_MINUS) {
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(binding.imgMinusEvidence)

            binding.imgMinusEvidence.setPadding(0, 0, 0, 0)
            Toast.makeText(this, "Bukti Minus Ditambahkan", Toast.LENGTH_SHORT).show()

        } else if (currentPhotoMode == MODE_MOD) {
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(binding.imgModEvidence)

            binding.imgModEvidence.setPadding(0, 0, 0, 0)
            Toast.makeText(this, "Bukti Modif Ditambahkan", Toast.LENGTH_SHORT).show()

        } else {
            Toast.makeText(this, "Foto utama ditambahkan", Toast.LENGTH_SHORT).show()
        }
        currentPhotoMode = MODE_MAIN
    }

    private fun updateImagesPreview() {
        displayImages.clear()
        displayImages.addAll(existingImageUrls)
        displayImages.addAll(selectedImageUris)

        val adapter = ImageSliderAdapter(displayImages) { pos -> showDeletePhotoDialog(pos) }
        binding.rvSelectedImages.adapter = adapter

        binding.tvPhotoCount.text = "${displayImages.size} Foto dipilih"

        if (displayImages.isNotEmpty()) {
            binding.rvSelectedImages.visibility = View.VISIBLE
            binding.llPhotoPlaceholder.visibility = View.GONE
            binding.btnAddMorePhotos.visibility = View.VISIBLE
        } else {
            binding.rvSelectedImages.visibility = View.GONE
            binding.llPhotoPlaceholder.visibility = View.VISIBLE
            binding.btnAddMorePhotos.visibility = View.GONE
        }
    }

    private fun showDeletePhotoDialog(position: Int) {
        AlertDialog.Builder(this).setTitle("Hapus?")
            .setPositiveButton("Ya") { _, _ -> removeItem(position) }
            .setNegativeButton("Batal", null).show()
    }

    private fun removeItem(position: Int) {
        if (position < existingImageUrls.size) existingImageUrls.removeAt(position) else selectedImageUris.removeAt(
            position - existingImageUrls.size
        )
        updateImagesPreview()
    }

    private fun showRejectionDialog(obj: String, uri: Uri) {
        AlertDialog.Builder(this).setTitle("Bukan Mobil?").setMessage("Terdeteksi: $obj")
            .setNegativeButton("Tetap Upload") { _, _ ->
                isListingSuspicious = true; addPhotoToList(uri)
            }.setPositiveButton("Ganti", null).show()
    }

    private fun showRejectionDialogForMultiple(
        obj: String, uri: Uri, uris: MutableList<Uri>, idx: Int
    ) {
        AlertDialog.Builder(this).setTitle("Foto Bermasalah").setMessage("Foto ke-${idx + 1}: $obj")
            .setNegativeButton("Tetap Upload") { _, _ ->
                isListingSuspicious =
                    true; selectedImageUris.add(uri); displayImages.add(uri); progressDialog.show(); checkImagesRecursively(
                uris, idx + 1
            )
            }.setPositiveButton("Lewati") { _, _ ->
                progressDialog.show(); checkImagesRecursively(
                uris, idx + 1
            )
            }.show()
    }

    private fun uploadImagesRecursive(index: Int) {
        if (index >= selectedImageUris.size) {
            val final = mutableListOf<String>(); final.addAll(existingImageUrls); final.addAll(
                uploadedImageUrls
            ); saveData(final); return
        }
        progressDialog.setMessage("Upload foto ${index + 1}..."); progressDialog.show()
        MediaManager.get().upload(selectedImageUris[index]).unsigned(UPLOAD_PRESET)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    var url = resultData["secure_url"] as String
                    if (url.startsWith("http://")) url = url.replace("http://", "https://")
                    uploadedImageUrls.add(url); uploadImagesRecursive(index + 1)
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    progressDialog.dismiss(); Toast.makeText(
                        this@SellCarActivity, "Gagal upload", Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            }).dispatch()
    }

    override fun onDestroy() {
        super.onDestroy(); priceModelHelper.close()
    }
}