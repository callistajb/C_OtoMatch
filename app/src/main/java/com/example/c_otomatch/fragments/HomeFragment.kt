package com.example.c_otomatch.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c_otomatch.CarDetailActivity
import com.example.c_otomatch.R
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.models.Car
// HAPUS Data.kt
// import com.example.c_otomatch.utils.Data
import com.google.firebase.firestore.FirebaseFirestore // IMPORT FIRESTORE

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CarAdapter
    private val allCarsList = mutableListOf<Car>()
    private val displayedCarList = mutableListOf<Car>()

    private lateinit var db: FirebaseFirestore
    private var isSortInitialized = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        db = FirebaseFirestore.getInstance()

        recyclerView = view.findViewById(R.id.rvCars)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = CarAdapter(
            displayedCarList,
            { car ->
                // Ini parameter ke-1 (onItemClicked)
                val intent = Intent(requireContext(), CarDetailActivity::class.java).apply {
                    putExtra("car_document_id", car.documentId)
                    putExtra("car_name", car.name)
                    putExtra("car_brand", car.brand)
                    putExtra("car_year", car.year)
                    putExtra("car_price", car.price)
                    putExtra("car_mileage", car.mileage)
                    putExtra("car_location", car.location)
                    putExtra("car_image_url", car.imageUrl)
                    putExtra("seller_name", car.sellerName)
                    putExtra("seller_contact", car.sellerContact)
                    putExtra("body_type", car.bodyType)
                    putExtra("color", car.color)
                    putExtra("transmission", car.transmission)
                    putExtra("fuel", car.fuel)
                    putExtra("km_range", car.kmRange)
                }
                startActivity(intent)
            },
            {
            },
            isSellFragment = false
        )

        recyclerView.adapter = adapter

        loadCarsFromFirestore()

        val spinnerSort = view.findViewById<Spinner>(R.id.spinnerSort)
        val sortOptions = listOf("Urutkan: Default", "Termurah", "Termahal", "Terbaru", "Terlama")
        val sortAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            sortOptions
        )
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSort.adapter = sortAdapter

        spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                itemView: View?,
                position: Int,
                id: Long
            ) {
                if (!isSortInitialized) {
                    isSortInitialized = true
                    return
                }
                // Urutkan list yang sedang ditampilkan
                val listToSort = displayedCarList.toMutableList()

                val sortedList = when (position) {
                    1 -> listToSort.sortedBy { safePriceToLong(it.price) }
                    2 -> listToSort.sortedByDescending { safePriceToLong(it.price) }
                    3 -> listToSort.sortedByDescending { it.year }
                    4 -> listToSort.sortedBy { it.year }
                    else -> allCarsList // Kembali ke list master jika "Default"
                }

                // Perbarui adapter dengan list yang sudah difilter/disortir
                displayedCarList.clear()
                displayedCarList.addAll(sortedList)
                adapter.updateList(displayedCarList)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        return view
    }

    private fun loadCarsFromFirestore() {
        db.collection("cars")
            .whereEqualTo("isSold", false) // Hanya tampilkan yang 'Tersedia'
            .get()
            .addOnSuccessListener { result ->
                allCarsList.clear()
                for (document in result) {
                    try {
                        val car = document.toObject(Car::class.java)
                        car.documentId = document.id
                        allCarsList.add(car)
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Error converting document to Car", e)
                    }
                }

                // Panggil filterCars dengan query kosong untuk memuat data awal
                filterCars(query = "")
            }
            .addOnFailureListener { exception ->
                Log.w("HomeFragment", "Error getting documents: ", exception)
                Toast.makeText(requireContext(), "Gagal memuat data mobil", Toast.LENGTH_SHORT).show()
            }
    }

    fun filterCars(query: String) {
        val filtered = if (query.isEmpty()) {
            allCarsList // Tampilkan semua jika query kosong
        } else {
            // Selalu filter dari list master
            allCarsList.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.brand.contains(query, ignoreCase = true)
            }
        }

        // Simpan hasil filter ke list yang ditampilkan
        displayedCarList.clear()
        displayedCarList.addAll(filtered)

        // Update adapter
        adapter.updateList(displayedCarList)
    }

    private fun safePriceToLong(priceStr: String?): Long {
        if (priceStr.isNullOrBlank()) return 0L
        val digits = priceStr.replace("[^0-9]".toRegex(), "")
        return digits.toLongOrNull() ?: 0L
    }
}