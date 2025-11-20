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
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c_otomatch.CarDetailActivity
import com.example.c_otomatch.R
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.utils.NumberTextWatcher // <-- IMPORT INI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import java.util.Date

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CarAdapter
    // Ini list master, nyimpen SEMUA data mobil dari server
    private val allCarsList = mutableListOf<Car>()
    // Ini list yg ditampilin ke user (setelah difilter/sort)
    private val displayedCarList = mutableListOf<Car>()

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var isSortInitialized = false

    // Deklarasi UI-nya
    private lateinit var spinnerSort: Spinner
    private lateinit var switchMyCars: SwitchCompat

    // Nambahin variabel buat nahan search query dari MainActivity
    private var currentSearchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Init Firebase, gaskeun
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Setup RecyclerView
        recyclerView = view.findViewById(R.id.rvCars)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Setup Adapter
        adapter = CarAdapter(
            displayedCarList, // Kasih list kosong dulu, nanti diisi
            { car ->
                // Kalo item diklik, lempar ke Detail
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
                    // Lempar data baru juga
                    putExtra("variant", car.variant)
                    putExtra("capacity", car.capacity)
                    putExtra("negatives", car.negatives)
                    putExtra("mods", car.mods)
                }
                startActivity(intent)
            },
            {
                // Biarin kosong, tombol SOLD ga ada di Home
            },
            isSellFragment = false
        )
        recyclerView.adapter = adapter

        // Setup Spinner Sorting
        spinnerSort = view.findViewById(R.id.spinnerSort)
        // Ganti "Default" jadi "Terbaru" biar lebih jelas
        val sortOptions = listOf("Urutkan: Terbaru", "Termurah", "Termahal", "Terlama")
        val sortAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            sortOptions
        )
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSort.adapter = sortAdapter

        // Kalo item spinner diganti, panggil fungsi filter utama
        spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                if (!isSortInitialized) {
                    isSortInitialized = true
                    return
                }
                // JANGAN panggil loadCarsFromFirestore(). Cukup panggil applyFiltersAndSort()
                applyFiltersAndSort()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Setup Switch "Mobil Saya"
        switchMyCars = view.findViewById(R.id.switchMyCars)
        // Kalo switch diganti, panggil fungsi filter utama juga
        // GA PERLU ambil data ulang dari server
        switchMyCars.setOnCheckedChangeListener { _, isChecked ->
            applyFiltersAndSort()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        // Tiap kali tab Home dibuka, refresh datanya dari server
        loadCarsFromFirestore()
    }

    // Fungsi ini cuma buat AMBIL data dari server
    private fun loadCarsFromFirestore() {
        Log.d("HOME_FRAGMENT_DEBUG", "Memuat data dari Firestore...")

        // Ini query utamanya: Ambil semua mobil yg BELUM TERJUAL
        db.collection("cars")
            .whereEqualTo("isSold", false)
            .get(Source.SERVER) // Paksa ambil dari server, BUKAN CACHE. Biar datanya 100% baru
            .addOnSuccessListener { result ->
                allCarsList.clear() // Kosongin list master
                for (document in result) {
                    try {
                        val car = document.toObject(Car::class.java)
                        car.documentId = document.id
                        allCarsList.add(car)
                    } catch (e: Exception) {
                        // Kalo ada data yg rusak (dari uploader lama), amanin pake try-catch
                        Log.e("HomeFragment", "Error converting document. Data: ${document.data}", e)
                    }
                }

                Log.d("HOME_FRAGMENT_DEBUG", "Total mobil (isSold=false) ditemukan: ${allCarsList.size}")

                // Setelah data dapet, baru kita filter + sort
                applyFiltersAndSort()
                // Reset spinner ke "Terbaru" (posisi 0)
                spinnerSort.setSelection(0, false) // 'false' biar ga trigger listener
            }
            .addOnFailureListener { exception ->
                Log.w("HomeFragment", "Error getting documents: ", exception)
                Toast.makeText(requireContext(), "Gagal memuat data mobil", Toast.LENGTH_SHORT).show()
            }
    }

    // Fungsi ini dipanggil dari MainActivity kalo user ngetik di search bar
    fun filterCars(query: String) {
        currentSearchQuery = query // Simpen query-nya
        applyFiltersAndSort() // Panggil fungsi filter utama
    }

    // --- INI FUNGSI UTAMA UNTUK FILTER & SORT (CLIENT-SIDE)---
    private fun applyFiltersAndSort() {

        // 1. Selalu mulai dari data master (semua mobil yg 'isSold = false')
        var filteredList = allCarsList.toMutableList()

        // 2. Filter "Mobil Saya" (dikerjain di HP, bukan di server)
        // INI YANG MEMPERBAIKI BUG "MOBIL SAYA"
        if (switchMyCars.isChecked) {
            val user = auth.currentUser
            if (user != null) {
                Log.d("HOME_FRAGMENT_DEBUG", "Filter 'Mobil Saya' AKTIF")
                filteredList = filteredList.filter {
                    // Cek UID penjual di data mobil == UID user yg login
                    it.sellerUid == user.uid
                }.toMutableList()
            }
        }

        // 3. Filter Search Query (dikerjain di HP)
        if (currentSearchQuery.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.name.contains(currentSearchQuery, ignoreCase = true) ||
                        it.brand.contains(currentSearchQuery, ignoreCase = true)
            }.toMutableList()
        }

        // 4. Terapkan sorting dari spinner (dikerjain di HP)
        val sortPosition = spinnerSort.selectedItemPosition
        val sortedList = when (sortPosition) {
            1 -> filteredList.sortedBy { safePriceToLong(it.price) } // Termurah
            2 -> filteredList.sortedByDescending { safePriceToLong(it.price) } // Termahal
            // Kalo createdAt-nya null (data lama), pake tanggal 0 (paling lama)
            3 -> filteredList.sortedBy { it.createdAt ?: Date(0) } // Terlama
            else -> filteredList.sortedByDescending {
                it.createdAt ?: Date(0)
            } // 0 = Terbaru (Default)
        }

        // 5. Baru update UI-nya
        displayedCarList.clear()
        displayedCarList.addAll(sortedList)
        adapter.updateList(displayedCarList)
    }

    private fun safePriceToLong(priceStr: String?): Long {
        if (priceStr.isNullOrBlank()) return 0L
        // Panggil helper 'cleanDigits' dari NumberTextWatcher
        val digits = NumberTextWatcher.cleanDigits(priceStr)
        return digits.toLongOrNull() ?: 0L
    }
}