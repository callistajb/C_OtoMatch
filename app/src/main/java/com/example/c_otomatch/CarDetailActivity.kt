package com.example.c_otomatch

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.c_otomatch.databinding.ActivityCarDetailBinding

class CarDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCarDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCarDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ambil data dari intent
        val carName = intent.getStringExtra("car_name")
        val carBrand = intent.getStringExtra("car_brand")
        val carYear = intent.getIntExtra("car_year", 0)
        val carPrice = intent.getStringExtra("car_price")
        val carMileage = intent.getStringExtra("car_mileage")
        val carLocation = intent.getStringExtra("car_location")
        val carImageResId = intent.getIntExtra("car_image", 0)

        // Tampilkan data ke layout
        if (carImageResId != 0) binding.imgCarDetail.setImageResource(carImageResId)
        binding.tvCarNameDetail.text = carName
        binding.tvCarBrandDetail.text = carBrand
        binding.tvCarYearDetail.text = "Tahun: $carYear"
        binding.tvCarPriceDetail.text = carPrice
        binding.tvCarMileageDetail.text = "Kilometer: $carMileage"
        binding.tvCarLocationDetail.text = "Lokasi: $carLocation"

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
