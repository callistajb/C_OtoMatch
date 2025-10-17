package com.example.c_otomatch.fragments

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.c_otomatch.R
import com.example.c_otomatch.utils.Prefs
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class EditProfileFragment : Fragment() {

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

    // Gallery picker
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
        ActivityResultCallback { uri: Uri? ->
            uri?.let {
                currentPhotoUri = it
                ivProfile.setImageURI(it)
            }
        })

    // Camera
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            bitmap?.let {
                val uri = saveBitmapToMediaStore(it)
                currentPhotoUri = uri
                ivProfile.setImageURI(uri)
            }
        }

    // Permission untuk kamera
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takePictureLauncher.launch(null)
        else Toast.makeText(requireContext(), "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
    }

    // Permission untuk lokasi
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
                        Toast.makeText(
                            requireContext(),
                            "Gagal mendapatkan lokasi. Isi manual saja.",
                            Toast.LENGTH_SHORT
                        ).show()
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
            parentFragmentManager.popBackStack()
        }
        btnUseMyLocation.setOnClickListener {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return v
    }

    private fun loadProfile() {
        etName.setText(Prefs.getName(requireContext()))
        etUsername.setText(Prefs.getEmail(requireContext()))
        etPhone.setText(Prefs.getPhone(requireContext()))
        etLocation.setText(Prefs.getLocation(requireContext()))

        Prefs.getProfileImageUri(requireContext())?.let {
            try {
                val uri = Uri.parse(it)
                currentPhotoUri = uri
                ivProfile.setImageURI(uri)
            } catch (_: Exception) {}
        }
    }

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

    private fun saveProfile() {
        val name = etName.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val location = etLocation.text.toString().trim()

        if (name.isBlank()) {
            etName.error = "Nama wajib diisi"
            return
        }

        Prefs.setName(requireContext(), name)
        Prefs.setEmail(requireContext(), username)
        Prefs.setPhone(requireContext(), phone)
        Prefs.setLocation(requireContext(), location)
        currentPhotoUri?.let { Prefs.setProfileImageUri(requireContext(), it.toString()) }

        Toast.makeText(requireContext(), "Profil diperbarui", Toast.LENGTH_SHORT).show()
    }

    private fun showChangePasswordDialog() {
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

                val stored = Prefs.getPassword(requireContext())
                if (stored.isNotBlank() && old != stored) {
                    Toast.makeText(requireContext(), "Password lama salah", Toast.LENGTH_SHORT).show()
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
                Prefs.setPassword(requireContext(), nw)
                Toast.makeText(requireContext(), "Password diubah", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
