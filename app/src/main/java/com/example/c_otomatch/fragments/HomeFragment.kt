package com.example.c_otomatch.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager // GANTI INI
import androidx.recyclerview.widget.RecyclerView
import com.example.c_otomatch.CarDetailActivity
import com.example.c_otomatch.MatchActivity
import com.example.c_otomatch.R
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.utils.NumberTextWatcher
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.Date

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CarAdapter
    private val allCarsList = mutableListOf<Car>()
    private val displayedCarList = mutableListOf<Car>()

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var spinnerSort: Spinner
    private lateinit var switchMyCars: SwitchCompat

    private var isSortInitialized = false
    private var currentSearchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        recyclerView = view.findViewById(R.id.rvCars)
        spinnerSort = view.findViewById(R.id.spinnerSort)
        switchMyCars = view.findViewById(R.id.switchMyCars)

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)

        adapter = CarAdapter(
            displayedCarList,
            onItemClicked = { car ->
                val intent = Intent(requireContext(), CarDetailActivity::class.java).apply {
                    putExtra("car_document_id", car.documentId)
                    putExtra("car_name", car.name)
                    putExtra("car_brand", car.brand)
                    putExtra("car_year", car.year)
                    putExtra("car_price", car.price)
                    putExtra("car_mileage", car.mileage)
                    putExtra("car_location", car.location)
                    val img = if (car.imageUrls.isNotEmpty()) car.imageUrls[0] else car.imageUrl
                    putExtra("car_image_url", img)
                    putExtra("seller_name", car.sellerName)
                    putExtra("seller_contact", car.sellerContact)
                    putExtra("body_type", car.bodyType)
                    putExtra("color", car.color)
                    putExtra("exactColor", car.exactColor)
                    putExtra("transmission", car.transmission)
                    putExtra("fuel", car.fuel)
                    putExtra("variant", car.variant)
                    putExtra("capacity", car.capacity)
                    putExtra("negatives", car.negatives)
                    putExtra("mods", car.mods)
                }
                startActivity(intent)
            },
            onMarkSoldClicked = {},
            isSellFragment = false
        )
        recyclerView.adapter = adapter

        setupSortSpinner()
        switchMyCars.setOnCheckedChangeListener { _, _ -> applyFiltersAndSort() }
    }

    override fun onResume() {
        super.onResume()
        loadUserWishlist()
        loadCarsFromFirestore()
    }

    private fun loadUserWishlist() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val wishlist = document.get("wishlist") as? List<String> ?: emptyList()
                    adapter.updateWishlist(wishlist)
                }
            }
    }

    private fun loadCarsFromFirestore() {
        db.collection("cars")
            .whereEqualTo("isSold", false)
            .get(Source.SERVER)
            .addOnSuccessListener { result ->
                allCarsList.clear()
                for (document in result) {
                    try {
                        val car = document.toObject(Car::class.java)
                        car.documentId = document.id
                        allCarsList.add(car)
                    } catch (e: Exception) { }
                }
                applyFiltersAndSort()
            }
    }

    fun filterCars(query: String) {
        currentSearchQuery = query
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        var filteredList = allCarsList.toList()

        if (switchMyCars.isChecked) {
            val user = auth.currentUser
            if (user != null) {
                filteredList = filteredList.filter { it.sellerUid == user.uid }
            }
        }

        if (currentSearchQuery.isNotEmpty()) {
            val q = currentSearchQuery.lowercase()
            filteredList = filteredList.filter { car ->
                car.name.lowercase().contains(q) ||
                        car.brand.lowercase().contains(q) ||
                        car.model.lowercase().contains(q)
            }
        }

        val sortedList = when (spinnerSort.selectedItemPosition) {
            1 -> filteredList.sortedBy { safePriceToLong(it.price) }
            2 -> filteredList.sortedByDescending { safePriceToLong(it.price) }
            3 -> filteredList.sortedBy { it.createdAt ?: Date(0) }
            else -> filteredList.sortedByDescending { it.createdAt ?: Date(0) }
        }

        displayedCarList.clear()
        displayedCarList.addAll(sortedList)
        adapter.updateList(displayedCarList)
    }

    private fun setupSortSpinner() {
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
    }

    private fun safePriceToLong(priceStr: String?): Long {
        val safeString = priceStr ?: ""
        if (safeString.isBlank()) return 0L
        return try {
            NumberTextWatcher.cleanDigits(safeString).toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L }
    }
}