package com.example.c_otomatch.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c_otomatch.R
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.utils.Data

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CarAdapter
    private lateinit var carList: MutableList<Car>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        recyclerView = view.findViewById(R.id.rvCars)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        carList = Data.carList.toMutableList()
        adapter = CarAdapter(carList) {}
        recyclerView.adapter = adapter

        return view
    }

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
}
