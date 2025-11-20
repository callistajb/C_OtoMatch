package com.example.c_otomatch.fragments

import android.Manifest
import android.app.ProgressDialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.c_otomatch.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileFragment : Fragment() {

    private val UPLOAD_PRESET = "OtoMatch_preset"

    private lateinit var ivProfile: ImageView
    private lateinit var etName: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPhone: EditText
    private lateinit var etLocation: EditText
    private lateinit var btnChangePassword: Button
    private lateinit var btnSaveProfile: Button
    private lateinit var btnUseMyLocation: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentPhotoUri: Uri? = null
    private var existingPhotoUrl: String? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressDialog: ProgressDialog

    // ... (Launcher-launcher ini aman, biarin aja) ...
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
        ActivityResultCallback { uri: Uri? ->
            uri?.let {
                currentPhotoUri = it
                ivProfile.setImageURI(it)
            }
        })
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            bitmap?.let {
                val uri = saveBitmapToMediaStore(it)
                currentPhotoUri = uri
                ivProfile.setImageURI(uri)
            }
        }
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takePictureLauncher.launch(null)
        else Toast.makeText(requireContext(), "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
    }
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val lat = location.latitude
                        val lon = location.longitude
                        etLocation.setText(String.format("Lat: %.6f, Lon: %.6f", lat, lon))
                    } else {
                        Toast.makeText(requireContext(), "Gagal mendapatkan lokasi", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(requireContext(), "Gagal mendapatkan lokasi", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Toast.makeText(requireContext(), "Izin lokasi tidak diberikan", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_edit_profile, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // --- HAPUS INI (MediaManager.init) KARENA UDAH DI MyApplication ---
        // MediaManager.init(requireContext(), hashMapOf("cloud_name" to CLOUD_NAME))
        // ------------------------------------------------------------------

        progressDialog = ProgressDialog(requireContext()).apply {
            setTitle("Menyimpan...")
            setCancelable(false)
        }

        ivProfile = v.findViewById(R.id.ivProfile)
        etName = v.findViewById(R.id.etName)
        etUsername = v.findViewById(R.id.etUsername)
        etPhone = v.findViewById(R.id.etPhone)
        etLocation = v.findViewById(R.id.etLocation)
        btnChangePassword = v.findViewById(R.id.btnChangePassword)
        btnSaveProfile = v.findViewById(R.id.btnSaveProfile)
        btnUseMyLocation = v.findViewById(R.id.btnUseMyLocation)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        loadProfile()

        ivProfile.setOnClickListener { showPhotoChooser() }
        btnChangePassword.setOnClickListener { showChangePasswordDialog() }
        btnSaveProfile.setOnClickListener {
            saveProfile()
        }
        btnUseMyLocation.setOnClickListener {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return v
    }

    private fun loadProfile() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    etName.setText(doc.getString("name"))
                    etUsername.setText(doc.getString("email"))
                    etUsername.isEnabled = false
                    etPhone.setText(doc.getString("phone"))
                    etLocation.setText(doc.getString("location"))
                    existingPhotoUrl = doc.getString("profileImageUrl")
                    if (!existingPhotoUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(existingPhotoUrl)
                            .placeholder(R.drawable.ic_person)
                            .circleCrop()
                            .into(ivProfile)
                    }
                }
            }
    }

    // ... (Fungsi showPhotoChooser, saveBitmap, showChangePasswordDialog TIDAK BERUBAH) ...
    private fun showPhotoChooser() {
        val options = arrayOf("Ambil Foto (Kamera)", "Pilih dari Galeri")
        AlertDialog.Builder(requireContext())
            .setTitle("Ganti Foto Profil")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val hasCameraPermission = ContextCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.CAMERA
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasCameraPermission) takePictureLauncher.launch(null)
                        else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap): Uri? {
        val filename = "profile_${System.currentTimeMillis()}.jpg"
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/OtoMatch")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = requireContext().contentResolver
        val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(requireContext(), "Gagal membuat file gambar", Toast.LENGTH_SHORT).show()
            return null
        }
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                if (!success) return null
            } ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return uri
    }

    private fun showChangePasswordDialog() {
        val user = auth.currentUser ?: return
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_change_password, null)
        val etOld = view.findViewById<EditText>(R.id.etOldPassword)
        val etNew = view.findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = view.findViewById<EditText>(R.id.etConfirmPassword)
        AlertDialog.Builder(requireContext())
            .setTitle("Ganti Password")
            .setView(view)
            .setPositiveButton("Simpan") { _, _ ->
                val old = etOld.text.toString()
                val nw = etNew.text.toString()
                val c = etConfirm.text.toString()
                if (old.isEmpty() || nw.isEmpty() || c.isEmpty()) {
                    Toast.makeText(requireContext(), "Semua field wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (nw.length < 6) {
                    Toast.makeText(requireContext(), "Password minimal 6 karakter", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (nw != c) {
                    Toast.makeText(requireContext(), "Konfirmasi password tidak cocok", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val credential = EmailAuthProvider.getCredential(user.email!!, old)
                user.reauthenticate(credential)
                    .addOnSuccessListener {
                        user.updatePassword(nw)
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Password berhasil diubah", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(requireContext(), "Gagal ubah password: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        if (it is FirebaseAuthInvalidCredentialsException) {
                            Toast.makeText(requireContext(), "Password lama salah", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun saveProfile() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "User tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }

        progressDialog.show()

        if (currentPhotoUri != null) {
            progressDialog.setMessage("Mengupload foto profil...")
            MediaManager.get().upload(currentPhotoUri!!)
                .unsigned(UPLOAD_PRESET)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val downloadUrl = resultData["secure_url"] as String
                        progressDialog.setMessage("Menyimpan profil...")
                        updateUserDocument(user.uid, downloadUrl)
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        progressDialog.dismiss()
                        Toast.makeText(requireContext(), "Gagal upload foto: ${error.description}", Toast.LENGTH_SHORT).show()
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                })
                .dispatch()
        } else {
            updateUserDocument(user.uid, existingPhotoUrl)
        }
    }

    private fun updateUserDocument(uid: String, imageUrl: String?) {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val location = etLocation.text.toString().trim()

        if (name.isBlank()) {
            etName.error = "Nama wajib diisi"
            progressDialog.dismiss()
            return
        }

        val userDataUpdates = hashMapOf<String, Any>(
            "name" to name,
            "phone" to phone,
            "location" to location
        )
        if (imageUrl != null) {
            userDataUpdates["profileImageUrl"] = imageUrl
        }

        db.collection("users").document(uid)
            .update(userDataUpdates)
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(requireContext(), "Profil diperbarui", Toast.LENGTH_SHORT).show()
                val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
                auth.currentUser?.updateProfile(profileUpdates)

                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(requireContext(), "Gagal simpan profil: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}