package com.example.c_otomatch.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c_otomatch.R
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.utils.Data

class WishlistFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: CarAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_wishlist, container, false)

        recyclerView = view.findViewById(R.id.rvWishlist)
        tvEmpty = view.findViewById(R.id.tvEmptyWishlist)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        updateWishlist()

        return view
    }

    override fun onResume() {
        super.onResume()
        updateWishlist()
    }

    private fun updateWishlist() {
        val wishlistCars = Data.carList.filter { it.isWishlist }

        if (wishlistCars.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            adapter = CarAdapter(wishlistCars) { /* nanti bisa tambahkan detail */ }
            recyclerView.adapter = adapter
        }
    }
}
