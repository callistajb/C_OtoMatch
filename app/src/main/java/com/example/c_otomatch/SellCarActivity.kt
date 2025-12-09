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
import com.example.c_otomatch.utils.NumberTextWatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

class SellCarActivity : AppCompatActivity() {

    private val UPLOAD_PRESET = "OtoMatch_Preset"
    private lateinit var binding: ActivityAddCarBinding
    private var selectedImageUris = mutableListOf<Uri>()
    private var uploadedImageUrls = mutableListOf<String>()
    private var existingImageUrls = mutableListOf<String>()

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialog
    private var editingCarId: String? = null

    private val pickMultipleImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris.addAll(uris)
            updateImagesPreview()
        }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val uri = getImageUriFromBitmap(bitmap)
            if (uri != null) {
                selectedImageUris.add(uri)
                updateImagesPreview()
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

    private fun updateImagesPreview() {
        val displayList = mutableListOf<Any>()
        displayList.addAll(existingImageUrls)
        displayList.addAll(selectedImageUris)

        val adapter = ImageSliderAdapter(displayList)
        binding.rvSelectedImages.adapter = adapter
        binding.tvPhotoCount.text = "${displayList.size} Foto dipilih"
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

        progressDialog.setMessage("Mengupload foto ke-${index + 1} dari ${selectedImageUris.size}...")
        progressDialog.show()

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
                    progressDialog.dismiss()
                    Toast.makeText(this@SellCarActivity, "Gagal upload: ${error.description}", Toast.LENGTH_SHORT).show()
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            }).dispatch()
    }

    private fun saveData(finalImageUrls: List<String>) {
        progressDialog.setMessage("Menyimpan data...")

        val priceString = NumberTextWatcher.cleanDigits(binding.etPrice.text.toString())
        val mileageString = NumberTextWatcher.cleanDigits(binding.etMileage.text.toString())
        val capacityString = NumberTextWatcher.cleanDigits(binding.etCapacity.text.toString())

        val colorCategory = binding.actColorCategory.text.toString()
        var colorSpecific = binding.etExactColor.text.toString()
        if (colorSpecific.isBlank()) colorSpecific = colorCategory

        val carDataMap = hashMapOf<String, Any>(
            "name" to "${binding.etBrand.text} ${binding.etModel.text}",
            "brand" to binding.etBrand.text.toString(),
            "model" to binding.etModel.text.toString(),
            "year" to (binding.etYear.text.toString().toIntOrNull() ?: 2020),
            "price" to NumberTextWatcher.formatToRupiah(priceString.toLongOrNull() ?: 0),
            "mileage" to NumberTextWatcher.formatToKm(mileageString.toLongOrNull() ?: 0),
            "location" to binding.etLocation.text.toString(),

            "color" to colorCategory,
            "exactColor" to colorSpecific,

            "imageUrls" to finalImageUrls,
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
            db.collection("cars").document(editingCarId!!).update(carDataMap)
                .addOnSuccessListener {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                .addOnFailureListener {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Gagal update: ${it.message}", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Gagal posting: ${it.message}", Toast.LENGTH_SHORT).show()
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

            binding.etSellerName.setText(car.sellerName)
            binding.etSellerContact.setText(car.sellerContact)
            binding.actSellerType.setText(car.sellerType, false)
            binding.etBrand.setText(car.brand)
            binding.etModel.setText(car.model)
            binding.etYear.setText(car.year.toString())
            binding.etMileage.setText(NumberTextWatcher.cleanDigits(car.mileage))
            binding.etLocation.setText(car.location)

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
        if (binding.actColorCategory.text.isNullOrBlank()) {
            binding.actColorCategory.error = "Wajib pilih kategori"
            return false
        }
        return true
    }

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "Title_${System.currentTimeMillis()}", null)
        return Uri.parse(path)
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

        val colorCategories = arrayOf("Hitam", "Putih", "Silver", "Abu-abu", "Merah", "Biru", "Hijau", "Kuning", "Coklat", "Oranye", "Gold", "Ungu", "Lainnya")

        // GANTI KE SIMPLE_LIST_ITEM_1
        binding.actSellerType.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, sellerTypes))
        binding.actFuelType.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, fuelTypes))
        binding.actTransmission.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, transmissions))
        binding.actBodyType.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, bodyTypes))
        binding.actColorCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, colorCategories))
    }

    private fun generatePriceSuggestion(): String {
        val year = binding.etYear.text.toString().toIntOrNull() ?: 2020
        val base = if (year >= 2020) 200_000_000 else 100_000_000
        return "Rp %,d".format(base)
    }
}