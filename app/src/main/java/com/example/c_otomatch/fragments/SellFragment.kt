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
import com.example.c_otomatch.R
import com.example.c_otomatch.SellCarActivity
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.databinding.FragmentSellBinding
import com.example.c_otomatch.models.Car
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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
            { car -> showCarOptionsDialog(car) }, // Listener klik item (untuk dialog)
            { car -> toggleSoldStatus(car) },     // Listener klik tombol "Mark as SOLD"
            isSellFragment = true
        )

        binding.recyclerSellCars.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSellCars.adapter = adapter

        binding.fabAddCar.setOnClickListener {
            val intent = Intent(requireContext(), SellCarActivity::class.java)
            addCarLauncher.launch(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadMyCars()
    }

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
            .get()
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
                // Urutkan agar yang 'Tersedia' (isSold=false) muncul di atas
                myCarsList.sortBy { it.isSold }
                adapter.updateList(myCarsList)
            }
            .addOnFailureListener { e ->
                Log.e("SellFragment", "Error fetching cars", e)
                Toast.makeText(context, "Gagal memuat data", Toast.LENGTH_SHORT).show()
            }
    }

    private val addCarLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("SellFragment", "New car added, onResume will refresh.")
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
                    0 -> showEditCarDialog(car)
                    1 -> deleteCar(car)
                    2 -> toggleSoldStatus(car)
                }
            }
            .show()
    }

    private fun showEditCarDialog(car: Car) {
        // (Tidak ada perubahan di sini)
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.activity_add_car, null)
        val brand = dialogView.findViewById<EditText>(R.id.etBrand)
        val model = dialogView.findViewById<EditText>(R.id.etModel)
        val year = dialogView.findViewById<EditText>(R.id.etYear)
        val mileage = dialogView.findViewById<EditText>(R.id.etMileage)
        val location = dialogView.findViewById<EditText>(R.id.etLocation)
        val price = dialogView.findViewById<EditText>(R.id.etPrice)
        brand.setText(car.brand)
        model.setText(car.name.replace("${car.brand} ", ""))
        year.setText(car.year.toString())
        price.setText(car.price)
        mileage.setText(car.mileage)
        location.setText(car.location)
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Mobil")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val updates = hashMapOf<String, Any>(
                    "brand" to brand.text.toString(),
                    "name" to "${brand.text} ${model.text}",
                    "year" to (year.text.toString().toIntOrNull() ?: car.year),
                    "price" to price.text.toString(),
                    "mileage" to mileage.text.toString(),
                    "location" to location.text.toString()
                )
                db.collection("cars").document(car.documentId)
                    .update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Data mobil diperbarui", Toast.LENGTH_SHORT).show()
                        loadMyCars()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Gagal update", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteCar(car: Car) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Mobil")
            .setMessage("Apakah kamu yakin ingin menghapus mobil '${car.name}' dari daftar penjualan?")
            .setPositiveButton("Hapus") { _, _ ->
                db.collection("cars").document(car.documentId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Mobil berhasil dihapus", Toast.LENGTH_SHORT).show()
                        // Saat mobil dihapus, kita tetap hapus transaksinya
                        // agar tidak ada data "sampah"
                        deleteTransaction(car.documentId, false)
                        loadMyCars() // Refresh
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Gagal menghapus", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun toggleSoldStatus(car: Car) {
        val newStatus = !car.isSold
        val oldPosition = myCarsList.indexOf(car)
        if (oldPosition == -1) return // Pengaman

        // UPDATE UI INSTAN (OPTIMISTIC)
        car.isSold = newStatus // Ubah data di list lokal
        myCarsList.sortBy { it.isSold } // Urutkan ulang list lokal
        val newPosition = myCarsList.indexOf(car) // Cari posisi baru setelah diurut

        adapter.notifyItemMoved(oldPosition, newPosition)
        adapter.notifyItemChanged(newPosition) // Update tampilan (label SOLD)

        // KIRIM PERUBAHAN KE DATABASE DI BACKEND
        db.collection("cars").document(car.documentId)
            .update("isSold", newStatus)
            .addOnSuccessListener {
                Log.d("SellFragment", "Status 'isSold' berhasil diupdate di Firestore")

                // Update koleksi transactions
                if (newStatus == true) {
                    // --- JIKA BARU DIJUAL (SOLD) ---
                    // Buat data transaksi baru
                    val transactionData = hashMapOf<String, Any?>(
                        "carId" to car.documentId,
                        "carName" to car.name,
                        "sellerId" to auth.currentUser?.uid,
                        "salePrice" to car.price,
                        "soldDate" to com.google.firebase.Timestamp.now(),
                        "transactionStatus" to "COMPLETED", // Status: Selesai
                        "canceledDate" to null // Belum dibatalkan
                    )

                    // Simpan ke koleksi "transactions"
                    db.collection("transactions").add(transactionData)
                        .addOnFailureListener { e ->
                            Log.w("SellFragment", "Gagal catat transaksi", e)
                            rollbackSoldStatus(car, "Gagal mencatat transaksi")
                        }
                } else {
                    // --- JIKA DIKEMBALIKAN (UN-SOLD) ---
                    // Ga dihapus, tapi MENG-UPDATE transaksi yang ada
                    db.collection("transactions")
                        .whereEqualTo("carId", car.documentId)
                        .whereEqualTo("transactionStatus", "COMPLETED") // Cari yg statusnya 'COMPLETED'
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            if (querySnapshot.isEmpty) {
                                Log.w("SellFragment", "Tidak ada transaksi 'COMPLETED' untuk dibatalkan.")
                                return@addOnSuccessListener
                            }

                            // Update semua transaksi yg cocok (biasanya 1)
                            val batch = db.batch()
                            for (document in querySnapshot.documents) {
                                val updates = hashMapOf<String, Any?>(
                                    "transactionStatus" to "CANCELED",
                                    "canceledDate" to com.google.firebase.Timestamp.now()
                                )
                                batch.update(document.reference, updates)
                            }
                            batch.commit()
                                .addOnSuccessListener {
                                    Log.d("SellFragment", "Transaksi dibatalkan.")
                                }
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

    private fun rollbackSoldStatus(car: Car, errorMessage: String) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()

        val oldPosition = myCarsList.indexOf(car)
        if (oldPosition == -1) return

        car.isSold = !car.isSold // Kembalikan statusnya
        myCarsList.sortBy { it.isSold } // Urutkan lagi
        val newPosition = myCarsList.indexOf(car)

        adapter.notifyItemMoved(oldPosition, newPosition)
        adapter.notifyItemChanged(newPosition)
    }

    // Fungsi ini HANYA dipakai saat mobil di-delete permanen
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
                        if (!enableRollback) loadMyCars() // Refresh jika dipanggil dari deleteCar
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