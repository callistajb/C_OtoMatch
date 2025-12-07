package com.example.c_otomatch

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbarCompare.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        db = FirebaseFirestore.getInstance()

        val carId1 = intent.getStringExtra("CAR_ID_1")
        val carId2 = intent.getStringExtra("CAR_ID_2")

        if (!carId1.isNullOrEmpty() && !carId2.isNullOrEmpty()) {
            loadCars(carId1, carId2)
        } else {
            Toast.makeText(this, "Data mobil tidak valid", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadCars(id1: String, id2: String) {
        // Ambil Mobil 1
        db.collection("cars").document(id1).get()
            .addOnSuccessListener { doc1 ->
                // SAFETY CHECK 1: Jika Activity sudah ditutup, STOP. Jangan lanjut biar ga crash.
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val car1 = doc1.toObject(Car::class.java)

                // Ambil Mobil 2 (Nested, supaya dapet dua-duanya)
                db.collection("cars").document(id2).get()
                    .addOnSuccessListener { doc2 ->
                        // SAFETY CHECK 2: Cek lagi status Activity
                        if (isFinishing || isDestroyed) return@addOnSuccessListener

                        val car2 = doc2.toObject(Car::class.java)

                        if (car1 != null && car2 != null) {
                            displayComparison(car1, car2)
                        } else {
                            Toast.makeText(this, "Salah satu data mobil tidak ditemukan", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this, "Gagal memuat mobil kedua", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .addOnFailureListener {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Gagal memuat data", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun displayComparison(c1: Car, c2: Car) {
        // SAFETY CHECK 3: Pastikan Activity aman sebelum update UI/Gambar
        if (isFinishing || isDestroyed) return

        // --- 1. GAMBAR & NAMA ---
        // Prioritaskan gambar dari list jika ada
        val img1 = if (c1.imageUrls.isNotEmpty()) c1.imageUrls[0] else c1.imageUrl
        val img2 = if (c2.imageUrls.isNotEmpty()) c2.imageUrls[0] else c2.imageUrl

        // Menggunakan try-catch pada Glide untuk keamanan ekstra
        try {
            Glide.with(this).load(img1).placeholder(R.drawable.ic_car).error(R.drawable.ic_car).override(500,500).into(binding.imgCar1)
            Glide.with(this).load(img2).placeholder(R.drawable.ic_car).error(R.drawable.ic_car).override(500,500).into(binding.imgCar2)
        } catch (e: Exception) {
            // Abaikan jika glide gagal load saat destroy
        }

        binding.tvName1.text = c1.name
        binding.tvName2.text = c2.name

        // --- 2. HARGA (Murah = Hijau, Mahal = Merah) ---
        binding.tvPrice1.text = c1.price
        binding.tvPrice2.text = c2.price
        compareAndHighlightPrice(c1.price, c2.price, binding.tvPrice1, binding.tvPrice2)

        // --- 3. TAHUN (Baru = Hijau) ---
        binding.tvYear1.text = c1.year.toString()
        binding.tvYear2.text = c2.year.toString()
        compareAndHighlightNumber(c1.year.toLong(), c2.year.toLong(), binding.tvYear1, binding.tvYear2, higherIsBetter = true)

        // --- 4. TRANSMISI (Beda = Biru) ---
        binding.tvTrans1.text = c1.transmission
        binding.tvTrans2.text = c2.transmission
        compareAndHighlightText(c1.transmission, c2.transmission, binding.tvTrans1, binding.tvTrans2)

        // --- 5. BAHAN BAKAR (Beda = Biru) ---
        binding.tvFuel1.text = c1.fuel
        binding.tvFuel2.text = c2.fuel
        compareAndHighlightText(c1.fuel, c2.fuel, binding.tvFuel1, binding.tvFuel2)

        // --- 6. KAPASITAS MESIN (Besar = Hijau) ---
        binding.tvCap1.text = if (c1.capacity.isNotEmpty()) c1.capacity else "-"
        binding.tvCap2.text = if (c2.capacity.isNotEmpty()) c2.capacity else "-"
        val cap1 = cleanNumber(c1.capacity)
        val cap2 = cleanNumber(c2.capacity)
        compareAndHighlightNumber(cap1, cap2, binding.tvCap1, binding.tvCap2, higherIsBetter = true)

        // --- 7. JARAK TEMPUH (Sedikit = Hijau) ---
        binding.tvKm1.text = c1.mileage
        binding.tvKm2.text = c2.mileage
        val km1 = cleanNumber(c1.mileage)
        val km2 = cleanNumber(c2.mileage)
        compareAndHighlightNumber(km1, km2, binding.tvKm1, binding.tvKm2, higherIsBetter = false)
    }

    // --- LOGIKA HIGHLIGHTING ---

    // Membersihkan string "Rp 200.000" jadi Long 200000
    private fun cleanNumber(text: String): Long {
        return try {
            if (text.isBlank()) 0L
            else text.replace(Regex("[^0-9]"), "").toLong()
        } catch (e: Exception) {
            0L
        }
    }

    // Bandingkan Angka (Harga, Tahun, CC)
    private fun compareAndHighlightNumber(v1: Long, v2: Long, tv1: TextView, tv2: TextView, higherIsBetter: Boolean) {
        // Ambil warna dengan aman
        val colorBetter = ContextCompat.getColor(this, R.color.compare_better) // Hijau
        val colorWorse = ContextCompat.getColor(this, R.color.compare_worse)   // Merah
        val colorSame = Color.BLACK

        if (v1 == v2) {
            tv1.setTextColor(colorSame)
            tv2.setTextColor(colorSame)
            return
        }

        if (higherIsBetter) {
            // Makin tinggi makin bagus (Tahun, CC)
            if (v1 > v2) {
                tv1.setTextColor(colorBetter)
                tv1.setTypeface(null, Typeface.BOLD)
                tv2.setTextColor(colorWorse)
            } else {
                tv1.setTextColor(colorWorse)
                tv2.setTextColor(colorBetter)
                tv2.setTypeface(null, Typeface.BOLD)
            }
        } else {
            // Makin rendah makin bagus (KM, Harga)
            if (v1 < v2) {
                tv1.setTextColor(colorBetter)
                tv1.setTypeface(null, Typeface.BOLD)
                tv2.setTextColor(colorWorse)
            } else {
                tv1.setTextColor(colorWorse)
                tv2.setTextColor(colorBetter)
                tv2.setTypeface(null, Typeface.BOLD)
            }
        }
    }

    // Khusus Harga (karena stringnya ribet)
    private fun compareAndHighlightPrice(p1: String, p2: String, tv1: TextView, tv2: TextView) {
        val val1 = cleanNumber(p1)
        val val2 = cleanNumber(p2)
        // Harga lebih murah = Lebih Bagus
        compareAndHighlightNumber(val1, val2, tv1, tv2, higherIsBetter = false)
    }

    // Bandingkan Teks (Transmisi, BBM) - Hanya highlight kalau beda
    private fun compareAndHighlightText(t1: String, t2: String, tv1: TextView, tv2: TextView) {
        val colorDiff = ContextCompat.getColor(this, R.color.compare_diff) // Biru
        val colorSame = Color.BLACK

        if (t1.equals(t2, ignoreCase = true)) {
            tv1.setTextColor(colorSame)
            tv2.setTextColor(colorSame)
        } else {
            // Jika beda, warnai biru dan tebalkan agar user 'ngeh' ada beda
            tv1.setTextColor(colorDiff)
            tv1.setTypeface(null, Typeface.BOLD)

            tv2.setTextColor(colorDiff)
            tv2.setTypeface(null, Typeface.BOLD)
        }
    }
}