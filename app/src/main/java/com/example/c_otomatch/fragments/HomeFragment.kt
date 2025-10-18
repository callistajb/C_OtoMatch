package com.example.c_otomatch.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c_otomatch.CarDetailActivity
import com.example.c_otomatch.R
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.utils.Data

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CarAdapter
    private lateinit var carList: MutableList<Car>

    private var isSortInitialized = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        recyclerView = view.findViewById(R.id.rvCars)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // ambil data asli (salinan mutable)
        carList = Data.carList.toMutableList()

        adapter = CarAdapter(carList, { car ->
            val intent = Intent(requireContext(), CarDetailActivity::class.java).apply {
                putExtra("car_name", car.name)
                putExtra("car_brand", car.brand)
                putExtra("car_year", car.year)
                putExtra("car_price", car.price)
                putExtra("car_mileage", car.mileage)
                putExtra("car_location", car.location)
                putExtra("car_image", car.imageResId)
                putExtra("seller_name", car.sellerName)
                putExtra("seller_contact", car.sellerContact)
                putExtra("body_type", car.bodyType)
                putExtra("color", car.color)
                putExtra("transmission", car.transmission)
                putExtra("fuel", car.fuel)
                putExtra("km_range", car.kmRange)
            }
            startActivity(intent)
        })

        recyclerView.adapter = adapter

        // Spinner Sort
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
                // Abaikan pemanggilan pertama (inisialisasi)
                if (!isSortInitialized) {
                    isSortInitialized = true
                    return
                }

                val sortedList: List<Car> = when (position) {
                    1 -> carList.sortedBy { safePriceToLong(it.price) }                 // Termurah
                    2 -> carList.sortedByDescending { safePriceToLong(it.price) }      // Termahal
                    3 -> carList.sortedByDescending { it.year }                        // Terbaru
                    4 -> carList.sortedBy { it.year }                                  // Terlama
                    else -> Data.carList // Default: kembali ke data asli
                }

                adapter.updateList(sortedList)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // nothing
            }
        }

        return view
    }

    /**
     * Pencarian / filter dari search bar
     */
    fun filterCars(query: String) {
        val filtered = if (query.isEmpty()) {
            Data.carList
        } else {
            Data.carList.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.brand.contains(query, ignoreCase = true)
            }
        }
        adapter.updateList(filtered)
    }

    private fun safePriceToLong(priceStr: String?): Long {
        if (priceStr.isNullOrBlank()) return 0L
        // hapus non-digit
        val digits = priceStr.replace("[^0-9]".toRegex(), "")
        return digits.toLongOrNull() ?: 0L
    }
}
