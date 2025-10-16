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
import androidx.activity.result.contract.ActivityResultContracts
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

        // Dummy data awal
        myCars.addAll(
            listOf(
                Car(1, "Civic Turbo", "Honda", 2021, "Rp 420.000.000", "20.000 km", "Tangerang", R.drawable.civic, false),
                Car(2, "Fortuner VRZ", "Toyota", 2020, "Rp 520.000.000", "35.000 km", "Jakarta", R.drawable.fortuner, false)
            )
        )

        adapter = CarAdapter(myCars) { car ->
            showCarOptionsDialog(car)
        }

        binding.recyclerSellCars.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSellCars.adapter = adapter

        binding.fabAddCar.setOnClickListener {
            val intent = Intent(requireContext(), SellCarActivity::class.java)
            intent.putExtra("next_id", myCars.size + 1)
            addCarLauncher.launch(intent)
        }
    }

    // di class SellFragment : Fragment() { ... }
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
            val imageUriString = data.getStringExtra("image_uri")
            val imageRes = data.getIntExtra("image_res", R.drawable.ic_car)

            // If imageUriString available you may want to store it somewhere; adapter currently uses imageResId
            val imageToUse = if (!imageUriString.isNullOrBlank()) imageRes else imageRes

            val newCar = Car(
                id = id,
                name = name,
                brand = brand,
                year = year,
                price = price,
                mileage = mileage,
                location = location,
                imageResId = imageToUse,
                isWishlist = false,
                isSold = false
            )
            myCars.add(0, newCar) // insert at top
            adapter.notifyItemInserted(0)
            binding.recyclerSellCars.scrollToPosition(0)
        }
    }

    /**
     * Dialog untuk memilih aksi pada mobil (Edit / Hapus / Tandai Sold)
     */
    private fun showCarOptionsDialog(car: Car) {
        val options = if (car.isSold) arrayOf("Lihat Detail") else arrayOf("Edit", "Hapus", "Tandai Sold Out")

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

    /**
     * Edit data mobil yang sudah ada
     */
    private fun showEditCarDialog(car: Car) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.activity_add_car, null)

        // Ambil semua input field sesuai ID di XML
        val brand = dialogView.findViewById<EditText>(R.id.etBrand)
        val model = dialogView.findViewById<EditText>(R.id.etModel)
        val year = dialogView.findViewById<EditText>(R.id.etYear)
        val tax = dialogView.findViewById<EditText>(R.id.etTax)
        val color = dialogView.findViewById<EditText>(R.id.etColor)
        val mileage = dialogView.findViewById<EditText>(R.id.etMileage)
        val location = dialogView.findViewById<EditText>(R.id.etLocation)
        val negatives = dialogView.findViewById<EditText>(R.id.etNegatives)
        val mods = dialogView.findViewById<EditText>(R.id.etMods)
        val price = dialogView.findViewById<EditText>(R.id.etPrice)

        // Isi data lama (yang udah ada di objek Car)
        brand.setText(car.brand)
        model.setText(car.name.replace("${car.brand} ", "")) // asumsi name = "Brand Model"
        year.setText(car.year.toString())
        price.setText(car.price)
        mileage.setText(car.mileage)
        location.setText(car.location)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Mobil")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                // Simpan hasil edit ke dalam objek Car
                car.brand = brand.text.toString()
                car.name = "${brand.text} ${model.text}"
                car.year = year.text.toString().toIntOrNull() ?: car.year
                car.price = price.text.toString()
                car.mileage = mileage.text.toString()
                car.location = location.text.toString()
                // Opsional: kalau mau tambahkan tax, color, dll. ke model Car, bisa tambahkan field baru di class-nya

                adapter.notifyDataSetChanged()
                Toast.makeText(requireContext(), "Data mobil diperbarui", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /**
     * Hapus mobil dari daftar
     */
    private fun deleteCar(car: Car) {
        val position = myCars.indexOf(car)
        if (position != -1) {
            myCars.removeAt(position)
            adapter.notifyItemRemoved(position)
            Toast.makeText(requireContext(), "Mobil dihapus", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Tandai / hapus status Sold
     */
    private fun toggleSoldStatus(car: Car) {
        car.price = if (car.price.contains("SOLD", true)) {
            car.price.replace(" - SOLD", "", true)
        } else {
            "${car.price} - SOLD"
        }
        adapter.notifyDataSetChanged()
        Toast.makeText(requireContext(), "Status mobil diperbarui", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
