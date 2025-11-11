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
// IMPORT FIREBASE
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query // Untuk sorting

class SellFragment : Fragment() {

    private var _binding: FragmentSellBinding? = null
    private val binding get() = _binding!!

    private val myCarsList = mutableListOf<Car>()
    private lateinit var adapter: CarAdapter

    // Firebase
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

        adapter = CarAdapter(myCarsList, { car -> showCarOptionsDialog(car) }, isSellFragment = true)

        binding.recyclerSellCars.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSellCars.adapter = adapter

        binding.fabAddCar.setOnClickListener {
            val intent = Intent(requireContext(), SellCarActivity::class.java)
            addCarLauncher.launch(intent)
        }

        loadMyCars()
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
                adapter.updateList(myCarsList)
            }
            .addOnFailureListener { e ->
                Log.e("SellFragment", "Error fetching cars", e)
                Toast.makeText(context, "Gagal memuat data", Toast.LENGTH_SHORT).show()
            }
    }

    // Launcher untuk menerima data dari SellCarActivity
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

                // Update ke Firestore
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
                // Hapus dokumen dari Firestore
                db.collection("cars").document(car.documentId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Mobil berhasil dihapus", Toast.LENGTH_SHORT).show()
                        loadMyCars()
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

        // Update field "isSold" di Firestore
        db.collection("cars").document(car.documentId)
            .update("isSold", newStatus)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Status mobil diperbarui", Toast.LENGTH_SHORT).show()
                loadMyCars()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Gagal update status", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}