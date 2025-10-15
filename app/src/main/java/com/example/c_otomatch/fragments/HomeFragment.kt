package com.example.c_otomatch.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c_otomatch.CarDetailActivity
import com.example.c_otomatch.R
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.utils.Data

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvCars)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Adapter dengan klik → pindah ke detail mobil
        val adapter = CarAdapter(Data.carList) { selectedCar ->

            // Intent ke CarDetailActivity
            val intent = Intent(requireContext(), CarDetailActivity::class.java).apply {
                putExtra("car_name", selectedCar.name)
                putExtra("car_brand", selectedCar.brand)
                putExtra("car_year", selectedCar.year)
                putExtra("car_price", selectedCar.price)
                putExtra("car_mileage", selectedCar.mileage)
                putExtra("car_location", selectedCar.location)
                putExtra("car_image", selectedCar.imageResId)
            }

            startActivity(intent)
        }

        recyclerView.adapter = adapter
        return view
    }
}
