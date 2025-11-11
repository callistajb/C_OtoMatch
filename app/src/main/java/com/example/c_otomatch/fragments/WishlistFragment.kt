package com.example.c_otomatch.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c_otomatch.CarDetailActivity
import com.example.c_otomatch.R
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.models.Car
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore

class WishlistFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: CarAdapter
    private val wishlistCars = mutableListOf<Car>()

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_wishlist, container, false)

        // Inisialisasi Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvEmpty = view.findViewById(R.id.tvEmptyWishlist)
        recyclerView = view.findViewById(R.id.rvWishlist)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = CarAdapter(
            carList = wishlistCars,
            onItemClicked = { car ->
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
            isSellFragment = false
        )
        recyclerView.adapter = adapter

        return view
    }

    override fun onResume() {
        super.onResume()
        loadWishlist()
    }

    private fun loadWishlist() {
        val user = auth.currentUser
        if (user == null) {
            showEmpty(true)
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document == null || !document.exists()) {
                    Log.w("WishlistFragment", "User document not found")
                    showEmpty(true)
                    return@addOnSuccessListener
                }

                val wishlistIds = document.get("wishlist") as? List<String>

                if (wishlistIds.isNullOrEmpty()) {
                    showEmpty(true)
                    return@addOnSuccessListener
                }

                fetchCarsFromIds(wishlistIds)

            }
            .addOnFailureListener { e ->
                Log.e("WishlistFragment", "Error fetching user document", e)
                Toast.makeText(context, "Gagal memuat wishlist", Toast.LENGTH_SHORT).show()
                showEmpty(true)
            }
    }

    private fun fetchCarsFromIds(carIds: List<String>) {
        db.collection("cars")
            .whereIn(FieldPath.documentId(), carIds) // Query mobil berdasarkan ID dokumen
            .get()
            .addOnSuccessListener { result ->
                wishlistCars.clear()
                for (document in result) {
                    try {
                        val car = document.toObject(Car::class.java)
                        car.documentId = document.id
                        // Cek status isWishlist secara lokal (agar icon hati menyala)
                        car.isWishlist = true
                        wishlistCars.add(car)
                    } catch (e: Exception) {
                        Log.e("WishlistFragment", "Error converting car", e)
                    }
                }
                adapter.updateList(wishlistCars)
                showEmpty(wishlistCars.isEmpty())
            }
            .addOnFailureListener { e ->
                Log.e("WishlistFragment", "Error fetching cars by ID", e)
                showEmpty(true)
            }
    }

    private fun showEmpty(isEmpty: Boolean) {
        if (isEmpty) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            wishlistCars.clear()
            adapter.notifyDataSetChanged()
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
}