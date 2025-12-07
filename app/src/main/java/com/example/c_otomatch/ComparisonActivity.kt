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

        try {
            binding = ActivityComparisonBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error Layout: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setSupportActionBar(binding.toolbarCompare)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Bandingkan Mobil"

        db = FirebaseFirestore.getInstance()

        val carId1 = intent.getStringExtra("CAR_ID_1")
        val carId2 = intent.getStringExtra("CAR_ID_2")

        // Cek apakah ID valid (tidak null dan tidak kosong)
        if (!carId1.isNullOrEmpty() && !carId2.isNullOrEmpty()) {
            loadCars(carId1, carId2)
        } else {
            Toast.makeText(this, "Data mobil tidak valid", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadCars(id1: String, id2: String) {
        // Load Mobil 1
        db.collection("cars").document(id1).get()
            .addOnSuccessListener { doc1 ->
                val car1 = doc1.toObject(Car::class.java)

                // Load Mobil 2
                db.collection("cars").document(id2).get()
                    .addOnSuccessListener { doc2 ->
                        val car2 = doc2.toObject(Car::class.java)

                        if (!isFinishing && !isDestroyed) {
                            if (car1 != null && car2 != null) {
                                displayComparison(car1, car2)
                            } else {
                                Toast.makeText(this, "Gagal memuat detail mobil", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Gagal memuat mobil kedua", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat mobil pertama", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayComparison(c1: Car, c2: Car) {
        try {
            // Mobil 1
            Glide.with(this).load(c1.imageUrl).placeholder(R.drawable.ic_car).into(binding.imgCar1)
            binding.tvName1.text = c1.name
            binding.tvPrice1.text = c1.price
            binding.tvYear1.text = c1.year.toString()
            binding.tvTrans1.text = c1.transmission
            binding.tvFuel1.text = c1.fuel
            binding.tvCap1.text = "${c1.capacity} cc"

            // Mobil 2
            Glide.with(this).load(c2.imageUrl).placeholder(R.drawable.ic_car).into(binding.imgCar2)
            binding.tvName2.text = c2.name
            binding.tvPrice2.text = c2.price
            binding.tvYear2.text = c2.year.toString()
            binding.tvTrans2.text = c2.transmission
            binding.tvFuel2.text = c2.fuel
            binding.tvCap2.text = "${c2.capacity} cc"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}