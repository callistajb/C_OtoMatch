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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

class WishlistFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: CarAdapter
    private val wishlistCars = mutableListOf<Car>()

    // Firebase
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
                    val img = if (car.imageUrls.isNotEmpty()) car.imageUrls[0] else car.imageUrl
                    putExtra("car_image_url", img)
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
            onMarkSoldClicked = {
                // Fitur sell tidak dipakai di sini
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
                if (document != null && document.exists()) {
                    val wishlistIds = document.get("wishlist") as? List<String>

                    // PENTING: Update juga list ID di adapter agar icon love menyala merah
                    if (wishlistIds != null) {
                        adapter.updateWishlist(wishlistIds)
                    }

                    if (wishlistIds.isNullOrEmpty()) {
                        showEmpty(true)
                    } else {
                        fetchCarsFromIds(wishlistIds)
                    }
                } else {
                    showEmpty(true)
                }
            }
            .addOnFailureListener { e ->
                Log.e("WishlistFragment", "Error fetching user document", e)
                Toast.makeText(context, "Gagal memuat wishlist", Toast.LENGTH_SHORT).show()
                showEmpty(true)
            }
    }

    private fun fetchCarsFromIds(carIds: List<String>) {
        if (carIds.isEmpty()) {
            showEmpty(true)
            return
        }

        // --- SOLUSI LIMIT 10 ITEM FIRESTORE ---
        // Kita pecah list ID menjadi beberapa bagian (chunk), masing-masing max 10 ID
        val chunks = carIds.chunked(10)
        val tasks = chunks.map { chunk ->
            db.collection("cars")
                .whereIn(FieldPath.documentId(), chunk)
                .get()
        }

        // Jalankan semua query secara paralel
        Tasks.whenAllSuccess<QuerySnapshot>(tasks)
            .addOnSuccessListener { results ->
                wishlistCars.clear()

                // Gabungkan hasil dari semua chunk
                for (snapshot in results) {
                    for (document in snapshot) {
                        try {
                            val car = document.toObject(Car::class.java)
                            car.documentId = document.id
                            // Paksa true karena ini halaman wishlist
                            car.isWishlist = true
                            wishlistCars.add(car)
                        } catch (e: Exception) {
                            Log.e("WishlistFragment", "Error converting car", e)
                        }
                    }
                }

                adapter.updateList(wishlistCars)
                showEmpty(wishlistCars.isEmpty())
            }
            .addOnFailureListener { e ->
                Log.e("WishlistFragment", "Error fetching cars chunk", e)
                showEmpty(true)
            }
    }

    private fun showEmpty(isEmpty: Boolean) {
        if (isEmpty) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            // Bersihkan list agar tidak ada data hantu
            wishlistCars.clear()
            adapter.notifyDataSetChanged()
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
}