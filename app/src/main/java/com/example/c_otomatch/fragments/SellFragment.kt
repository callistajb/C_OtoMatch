package com.example.c_otomatch.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.example.c_otomatch.R
import com.example.c_otomatch.SellCarActivity // <-- Pastiin ini di-import
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.databinding.FragmentSellBinding
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.utils.NumberTextWatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source // <-- INI DIA YANG HILANG
import java.text.NumberFormat
import java.util.Locale

class SellFragment : Fragment() {

    private var _binding: FragmentSellBinding? = null
    private val binding get() = _binding!!

    private val myCarsList = mutableListOf<Car>()
    private lateinit var adapter: CarAdapter
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSellBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        adapter = CarAdapter(
            myCarsList,
            { car -> showCarOptionsDialog(car) }, // Klik di card
            { car -> toggleSoldStatus(car) },     // Klik di tombol 'Mark as SOLD'
            isSellFragment = true
        )

        binding.recyclerSellCars.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSellCars.adapter = adapter

        // Tombol + (FAB) buat JUAL BARU
        binding.fabAddCar.setOnClickListener {
            // Buka SellCarActivity tanpa ngirim ID
            val intent = Intent(requireContext(), SellCarActivity::class.java)
            addCarLauncher.launch(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadMyCars() // Refresh data tiap kali tab ini dibuka
    }

    // Ambil mobil yang sellerUid-nya = uid-ku
    private fun loadMyCars() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(context, "Anda harus login untuk melihat mobil Anda", Toast.LENGTH_SHORT).show()
            myCarsList.clear()
            adapter.notifyDataSetChanged()
            return
        }

        db.collection("cars")
            .whereEqualTo("sellerUid", user.uid)
            .get(Source.SERVER) // Ambil dari server aja biar datanya fresh
            .addOnSuccessListener { result ->
                myCarsList.clear()
                for (document in result) {
                    try {
                        val car = document.toObject(Car::class.java)
                        car.documentId = document.id
                        myCarsList.add(car)
                    } catch (e: Exception) {
                        Log.e("SellFragment", "Error converting car", e)
                    }
                }
                myCarsList.sortBy { it.isSold } // Yang sold pindah ke bawah
                adapter.updateList(myCarsList)
            }
            .addOnFailureListener { e ->
                Log.e("SellFragment", "Error fetching cars", e)
                Toast.makeText(context, "Gagal memuat data", Toast.LENGTH_SHORT).show()
            }
    }

    // Launcher ini dipake buat JUAL dan EDIT
    // Kalo sukses, kita refresh list
    private val addCarLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("SellFragment", "SellCarActivity selesai, onResume bakal refresh.")
            // Ga usah panggil loadMyCars() di sini,
            // onResume() udah otomatis manggil pas kita balik ke fragment ini
        }
    }

    private fun showCarOptionsDialog(car: Car) {
        val options = if (car.isSold)
            arrayOf("Edit", "Hapus", "Tandai TERSEDIA")
        else
            arrayOf("Edit", "Hapus", "Tandai SOLD OUT")

        AlertDialog.Builder(requireContext())
            .setTitle(car.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startEditCarActivity(car) // <-- Ini GANTI JADI FUNGSI BARU
                    1 -> deleteCar(car)
                    2 -> toggleSoldStatus(car)
                }
            }
            .show()
    }

    // --- ⬇️ INI FUNGSI BARU UNTUK EDIT ⬇️ ---
    // Buka SellCarActivity tapi kirim ID mobilnya
    private fun startEditCarActivity(car: Car) {
        val intent = Intent(requireContext(), SellCarActivity::class.java)
        intent.putExtra("EDIT_CAR_ID", car.documentId) // Kirim ID-nya
        addCarLauncher.launch(intent) // Pake launcher yg sama
    }
    // --- ⬆️ SELESAI FUNGSI BARU ⬆️ ---

    // --- FUNGSI showEditCarDialog(car) SEKARANG DIHAPUS KARENA GA KEPAKE LAGI ---

    private fun deleteCar(car: Car) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Mobil")
            .setMessage("Apakah kamu yakin ingin menghapus mobil '${car.name}' dari daftar penjualan?")
            .setPositiveButton("Hapus") { _, _ ->
                // TODO: Hapus juga gambar dari Cloudinary (fitur tambahan nanti)

                db.collection("cars").document(car.documentId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Mobil berhasil dihapus", Toast.LENGTH_SHORT).show()
                        deleteTransaction(car.documentId, false)
                        loadMyCars() // Langsung refresh
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Gagal menghapus", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun toggleSoldStatus(car: Car) {
        // Ini logika Optimistic UI, UI-nya diubah duluan
        val newStatus = !car.isSold
        val oldPosition = myCarsList.indexOf(car)
        if (oldPosition == -1) return

        car.isSold = newStatus
        myCarsList.sortBy { it.isSold }
        val newPosition = myCarsList.indexOf(car)

        adapter.notifyItemMoved(oldPosition, newPosition)
        adapter.notifyItemChanged(newPosition)

        // Baru update database di belakang layar
        db.collection("cars").document(car.documentId)
            .update("isSold", newStatus)
            .addOnSuccessListener {
                Log.d("SellFragment", "Status 'isSold' berhasil diupdate di Firestore")

                if (newStatus == true) {
                    // Kalo jadi SOLD, bikin transaksi
                    val transactionData = hashMapOf<String, Any?>(
                        "carId" to car.documentId, "carName" to car.name,
                        "sellerId" to auth.currentUser?.uid, "salePrice" to car.price,
                        "soldDate" to com.google.firebase.Timestamp.now(),
                        "transactionStatus" to "COMPLETED", "canceledDate" to null
                    )
                    db.collection("transactions").add(transactionData)
                        .addOnFailureListener { e ->
                            Log.w("SellFragment", "Gagal catat transaksi", e)
                            rollbackSoldStatus(car, "Gagal mencatat transaksi")
                        }
                } else {
                    // Kalo jadi UN-SOLD, update transaksinya jadi CANCELED
                    db.collection("transactions")
                        .whereEqualTo("carId", car.documentId)
                        .whereEqualTo("transactionStatus", "COMPLETED")
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            if (querySnapshot.isEmpty) {
                                Log.w("SellFragment", "Tidak ada transaksi 'COMPLETED' untuk dibatalkan.")
                                return@addOnSuccessListener
                            }
                            val batch = db.batch()
                            for (document in querySnapshot.documents) {
                                val updates = hashMapOf<String, Any?>(
                                    "transactionStatus" to "CANCELED",
                                    "canceledDate" to com.google.firebase.Timestamp.now()
                                )
                                batch.update(document.reference, updates)
                            }
                            batch.commit()
                                .addOnSuccessListener { Log.d("SellFragment", "Transaksi dibatalkan.") }
                                .addOnFailureListener { e ->
                                    Log.w("SellFragment", "Gagal update transaksi batch", e)
                                    rollbackSoldStatus(car, "Gagal batal transaksi")
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.w("SellFragment", "Gagal cari transaksi utk dibatalkan", e)
                            rollbackSoldStatus(car, "Gagal cari transaksi")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("SellFragment", "Gagal update status 'isSold'", e)
                rollbackSoldStatus(car, "Gagal update: ${e.message}")
            }
    }

    // Kalo update ke Firestore gagal, balikin UI-nya
    private fun rollbackSoldStatus(car: Car, errorMessage: String) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        val oldPosition = myCarsList.indexOf(car)
        if (oldPosition == -1) return
        car.isSold = !car.isSold // Balikin statusnya
        myCarsList.sortBy { it.isSold }
        val newPosition = myCarsList.indexOf(car)
        adapter.notifyItemMoved(oldPosition, newPosition)
        adapter.notifyItemChanged(newPosition)
    }

    // Kalo mobil dihapus, hapus juga transaksinya
    private fun deleteTransaction(carId: String, enableRollback: Boolean) {
        db.collection("transactions")
            .whereEqualTo("carId", carId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val batch = db.batch()
                for (document in querySnapshot.documents) {
                    batch.delete(document.reference)
                }
                batch.commit()
                    .addOnSuccessListener {
                        Log.d("SellFragment", "Transaksi terkait mobil yg dihapus, ikut dihapus.")
                        if (!enableRollback) loadMyCars()
                    }
                    .addOnFailureListener { e ->
                        Log.w("SellFragment", "Gagal hapus transaksi batch", e)
                        if(enableRollback) {
                            val carToRollback = myCarsList.find { it.documentId == carId }
                            if (carToRollback != null) {
                                rollbackSoldStatus(carToRollback, "Gagal batal transaksi")
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                Log.w("SellFragment", "Gagal cari transaksi utk dihapus", e)
                if(enableRollback) {
                    val carToRollback = myCarsList.find { it.documentId == carId }
                    if (carToRollback != null) {
                        rollbackSoldStatus(carToRollback, "Gagal cari transaksi")
                    }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}