package com.example.c_otomatch.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

        val adapter = CarAdapter(Data.carList) { selectedCar ->
            Toast.makeText(requireContext(), "Klik: ${selectedCar.name}", Toast.LENGTH_SHORT).show()
            // Nanti di sini bisa intent ke CarDetailActivity
        }

        recyclerView.adapter = adapter

        return view
    }
}
