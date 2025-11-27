package com.example.c_otomatch

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.c_otomatch.databinding.ActivityComparisonBinding
import com.example.c_otomatch.models.Car
import com.google.firebase.firestore.FirebaseFirestore

class ComparisonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComparisonBinding
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComparisonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarCompare)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Bandingkan Mobil"

        db = FirebaseFirestore.getInstance()

        // Ambil ID mobil dari HomeFragment
        val carId1 = intent.getStringExtra("CAR_ID_1")
        val carId2 = intent.getStringExtra("CAR_ID_2")

        if (carId1 != null && carId2 != null) {
            loadCars(carId1, carId2)
        } else {
            Toast.makeText(this, "Data mobil tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadCars(id1: String, id2: String) {
        // Ambil Mobil 1
        db.collection("cars").document(id1).get().addOnSuccessListener { doc1 ->
            val car1 = doc1.toObject(Car::class.java)

            // Ambil Mobil 2 (Nested biar urut)
            db.collection("cars").document(id2).get().addOnSuccessListener { doc2 ->
                val car2 = doc2.toObject(Car::class.java)

                // Cek apakah activity masih aktif sebelum update UI (PENTING: Anti Crash Glide)
                if (!isFinishing && !isDestroyed) {
                    if (car1 != null && car2 != null) {
                        displayComparison(car1, car2)
                    } else {
                        Toast.makeText(this, "Gagal memuat data mobil", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Koneksi bermasalah", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayComparison(c1: Car, c2: Car) {
        // --- MOBIL 1 (KIRI) ---
        Glide.with(this).load(c1.imageUrl).placeholder(R.drawable.ic_car).into(binding.imgCar1)
        binding.tvName1.text = c1.name
        binding.tvPrice1.text = c1.price
        binding.tvYear1.text = c1.year.toString()
        binding.tvTrans1.text = c1.transmission
        binding.tvFuel1.text = c1.fuel
        binding.tvCap1.text = "${c1.capacity} cc"

        // --- MOBIL 2 (KANAN) ---
        Glide.with(this).load(c2.imageUrl).placeholder(R.drawable.ic_car).into(binding.imgCar2)
        binding.tvName2.text = c2.name
        binding.tvPrice2.text = c2.price
        binding.tvYear2.text = c2.year.toString()
        binding.tvTrans2.text = c2.transmission
        binding.tvFuel2.text = c2.fuel
        binding.tvCap2.text = "${c2.capacity} cc"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}