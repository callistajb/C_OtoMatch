package com.example.c_otomatch.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c_otomatch.CarDetailActivity
import com.example.c_otomatch.ComparisonActivity // Pastikan ini tidak merah
import com.example.c_otomatch.R
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.utils.NumberTextWatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.Date

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CarAdapter
    private val allCarsList = mutableListOf<Car>()
    private val displayedCarList = mutableListOf<Car>()

    // List untuk menyimpan mobil yang dipilih user
    private val selectedCarsForCompare = mutableListOf<Car>()

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var isSortInitialized = false

    private lateinit var spinnerSort: Spinner
    private lateinit var switchMyCars: SwitchCompat
    private lateinit var btnCompareFloating: Button // Tombol melayang
    private var currentSearchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        recyclerView = view.findViewById(R.id.rvCars)
        btnCompareFloating = view.findViewById(R.id.btnCompareFloating)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = CarAdapter(
            displayedCarList,
            { car ->
                // Klik Item -> Ke Detail
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
                    putExtra("variant", car.variant)
                    putExtra("capacity", car.capacity)
                    putExtra("negatives", car.negatives)
                    putExtra("mods", car.mods)
                }
                startActivity(intent)
            },
            {}, // Tombol sold ga dipake di home
            isSellFragment = false,

            // LOGIC CHECKBOX
            onCompareChecked = { car, isChecked ->
                if (isChecked) {
                    // Cegah duplikasi data yang sama
                    if (!selectedCarsForCompare.any { it.documentId == car.documentId }) {
                        selectedCarsForCompare.add(car)
                    }
                } else {
                    selectedCarsForCompare.removeAll { it.documentId == car.documentId }
                }
                updateCompareButton()
            }
        )
        recyclerView.adapter = adapter

        spinnerSort = view.findViewById(R.id.spinnerSort)
        val sortOptions = listOf("Urutkan: Terbaru", "Termurah", "Termahal", "Terlama")
        val sortAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSort.adapter = sortAdapter

        spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                if (!isSortInitialized) {
                    isSortInitialized = true
                    return
                }
                applyFiltersAndSort()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        switchMyCars = view.findViewById(R.id.switchMyCars)
        switchMyCars.setOnCheckedChangeListener { _, _ -> applyFiltersAndSort() }

        // KLIK TOMBOL BANDINGKAN
        btnCompareFloating.setOnClickListener {
            if (selectedCarsForCompare.size == 2) {
                val intent = Intent(requireContext(), ComparisonActivity::class.java)
                // Kirim Document ID (String) ke Activity sebelah
                intent.putExtra("CAR_ID_1", selectedCarsForCompare[0].documentId)
                intent.putExtra("CAR_ID_2", selectedCarsForCompare[1].documentId)
                startActivity(intent)

                // Reset pilihan setelah dibandingin
                adapter.clearSelection()
                selectedCarsForCompare.clear()
                updateCompareButton()
            }
        }

        return view
    }

    private fun updateCompareButton() {
        if (selectedCarsForCompare.size == 2) {
            btnCompareFloating.visibility = View.VISIBLE
            btnCompareFloating.text = "Bandingkan (${selectedCarsForCompare.size}/2)"
        } else {
            btnCompareFloating.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        loadCarsFromFirestore()
        // Reset selection pas balik ke home biar ga bingung
        selectedCarsForCompare.clear()
        adapter.clearSelection()
        updateCompareButton()
    }

    private fun loadCarsFromFirestore() {
        db.collection("cars").whereEqualTo("isSold", false).get(Source.SERVER).addOnSuccessListener { result ->
            allCarsList.clear()
            for (document in result) {
                try {
                    val car = document.toObject(Car::class.java)
                    car.documentId = document.id // PENTING: ID dokumen harus disimpan
                    allCarsList.add(car)
                } catch (e: Exception) {}
            }
            applyFiltersAndSort()
            spinnerSort.setSelection(0, false)
        }
    }

    fun filterCars(query: String) {
        currentSearchQuery = query
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        var filteredList = allCarsList.toMutableList()
        if (switchMyCars.isChecked) {
            val user = auth.currentUser
            if (user != null) filteredList = filteredList.filter { it.sellerUid == user.uid }.toMutableList()
        }
        if (currentSearchQuery.isNotEmpty()) {
            val q = currentSearchQuery.lowercase()
            filteredList = filteredList.filter { car ->
                car.name.lowercase().contains(q) || car.brand.lowercase().contains(q) ||
                        car.transmission.lowercase().contains(q) || car.fuel.lowercase().contains(q) ||
                        car.bodyType.lowercase().contains(q) || car.color.lowercase().contains(q)
            }.toMutableList()
        }
        val sortPosition = spinnerSort.selectedItemPosition
        val sortedList = when (sortPosition) {
            1 -> filteredList.sortedBy { safePriceToLong(it.price) }
            2 -> filteredList.sortedByDescending { safePriceToLong(it.price) }
            3 -> filteredList.sortedBy { it.createdAt ?: Date(0) }
            else -> filteredList.sortedByDescending { it.createdAt ?: Date(0) }
        }
        displayedCarList.clear()
        displayedCarList.addAll(sortedList)
        adapter.updateList(displayedCarList)
    }

    private fun safePriceToLong(priceStr: String?): Long {
        if (priceStr.isNullOrBlank()) return 0L
        val digits = NumberTextWatcher.cleanDigits(priceStr)
        return digits.toLongOrNull() ?: 0L
    }
}