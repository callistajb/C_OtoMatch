package com.example.c_otomatch.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.c_otomatch.R
import com.example.c_otomatch.SellCarActivity
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.databinding.FragmentSellBinding
import com.example.c_otomatch.models.Car

class SellFragment : Fragment() {

    private var _binding: FragmentSellBinding? = null
    private val binding get() = _binding!!
    private val myCars = mutableListOf<Car>()
    private lateinit var adapter: CarAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSellBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        myCars.addAll(
            listOf(
                Car(
                    id = 1,
                    name = "Civic Turbo",
                    brand = "Honda",
                    year = 2021,
                    price = "Rp 420.000.000",
                    mileage = "20.000 km",
                    location = "Tangerang",
                    imageResId = R.drawable.civic,
                    isWishlist = false,
                    isSold = false,
                    sellerName = "Anda",
                    sellerContact = "08123456789",
                    bodyType = "Sedan",
                    color = "Hitam",
                    transmission = "Automatic",
                    fuel = "Bensin",
                    kmRange = "<50.000 km"
                ),
                Car(
                    id = 2,
                    name = "Fortuner VRZ",
                    brand = "Toyota",
                    year = 2020,
                    price = "Rp 520.000.000",
                    mileage = "35.000 km",
                    location = "Jakarta",
                    imageResId = R.drawable.fortuner,
                    isWishlist = false,
                    isSold = false,
                    sellerName = "Anda",
                    sellerContact = "08123456789",
                    bodyType = "SUV",
                    color = "Putih",
                    transmission = "Automatic",
                    fuel = "Diesel",
                    kmRange = "50.000-100.000 km"
                )
            )
        )

        adapter = CarAdapter(myCars, { car -> showCarOptionsDialog(car) }, isSellFragment = true)

        binding.recyclerSellCars.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSellCars.adapter = adapter

        // Tombol tambah mobil baru
        binding.fabAddCar.setOnClickListener {
            val intent = Intent(requireContext(), SellCarActivity::class.java)
            intent.putExtra("next_id", myCars.size + 1)
            addCarLauncher.launch(intent)
        }
    }

    // Launcher untuk menerima data dari SellCarActivity
    private val addCarLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            val id = data.getIntExtra("id", myCars.size + 1)
            val name = data.getStringExtra("name") ?: "Unknown"
            val brand = data.getStringExtra("brand") ?: ""
            val year = data.getIntExtra("year", 2020)
            val price = data.getStringExtra("price") ?: "-"
            val mileage = data.getStringExtra("mileage") ?: "-"
            val location = data.getStringExtra("location") ?: "-"
            val imageRes = data.getIntExtra("image_res", R.drawable.ic_car)

            val newCar = Car(
                id = id,
                name = name,
                brand = brand,
                year = year,
                price = price,
                mileage = mileage,
                location = location,
                imageResId = imageRes,
                isWishlist = false,
                isSold = false,
                sellerName = "Anda",
                sellerContact = "08123456789",
                bodyType = "",
                color = "",
                transmission = "",
                fuel = "",
                kmRange = ""
            )

            myCars.add(0, newCar)
            adapter.notifyItemInserted(0)
            binding.recyclerSellCars.scrollToPosition(0)
        }
    }

    private fun showCarOptionsDialog(car: Car) {
        val options = if (car.isSold)
            arrayOf("Lihat Detail")
        else
            arrayOf("Edit", "Hapus", "Tandai Sold Out")

        AlertDialog.Builder(requireContext())
            .setTitle(car.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditCarDialog(car)
                    1 -> deleteCar(car)
                    2 -> toggleSoldStatus(car)
                }
            }
            .show()
    }

    private fun showEditCarDialog(car: Car) {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.activity_add_car, null)

        val brand = dialogView.findViewById<EditText>(R.id.etBrand)
        val model = dialogView.findViewById<EditText>(R.id.etModel)
        val year = dialogView.findViewById<EditText>(R.id.etYear)
        val mileage = dialogView.findViewById<EditText>(R.id.etMileage)
        val location = dialogView.findViewById<EditText>(R.id.etLocation)
        val price = dialogView.findViewById<EditText>(R.id.etPrice)

        brand.setText(car.brand)
        model.setText(car.name.replace("${car.brand} ", ""))
        year.setText(car.year.toString())
        price.setText(car.price)
        mileage.setText(car.mileage)
        location.setText(car.location)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Mobil")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                car.brand = brand.text.toString()
                car.name = "${brand.text} ${model.text}"
                car.year = year.text.toString().toIntOrNull() ?: car.year
                car.price = price.text.toString()
                car.mileage = mileage.text.toString()
                car.location = location.text.toString()
                adapter.notifyDataSetChanged()
                Toast.makeText(requireContext(), "Data mobil diperbarui", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteCar(car: Car) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Mobil")
            .setMessage("Apakah kamu yakin ingin menghapus mobil '${car.name}' dari daftar penjualan?")
            .setPositiveButton("Hapus") { _, _ ->
                val position = myCars.indexOf(car)
                if (position != -1) {
                    myCars.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    Toast.makeText(requireContext(), "Mobil berhasil dihapus", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun toggleSoldStatus(car: Car) {
        car.isSold = !car.isSold
        adapter.notifyDataSetChanged()
        Toast.makeText(requireContext(), "Status mobil diperbarui", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
