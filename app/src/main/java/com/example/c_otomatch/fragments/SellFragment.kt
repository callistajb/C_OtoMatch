package com.example.c_otomatch.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.c_otomatch.SellCarActivity
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.databinding.FragmentSellBinding
import com.example.c_otomatch.models.Car
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class SellFragment : Fragment() {

    private var _binding: FragmentSellBinding? = null
    private val binding get() = _binding!!
    private val myCarsList = mutableListOf<Car>()
    private lateinit var adapter: CarAdapter
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Launcher untuk refresh setelah edit/add
    private val addCarLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadMyCars()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSellBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        adapter = CarAdapter(
            myCarsList,
            { car -> showCarOptionsDialog(car) },
            { car -> toggleSoldStatus(car) },
            isSellFragment = true
        )

        binding.recyclerSellCars.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSellCars.adapter = adapter

        binding.fabAddCar.setOnClickListener {
            val intent = Intent(requireContext(), SellCarActivity::class.java)
            addCarLauncher.launch(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadMyCars()
    }

    private fun loadMyCars() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(context, "Anda harus login", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("cars")
            .whereEqualTo("sellerUid", user.uid)
            .get(Source.SERVER)
            .addOnSuccessListener { result ->
                myCarsList.clear()
                for (document in result) {
                    try {
                        val car = document.toObject(Car::class.java)
                        car.documentId = document.id
                        myCarsList.add(car)
                    } catch (e: Exception) {
                        Log.e("SellFragment", "Error converting car", e)
                    }
                }
                // Sort: Yang belum terjual di atas, yang terjual di bawah
                myCarsList.sortBy { it.isSold }
                adapter.updateList(myCarsList)
            }
            .addOnFailureListener {
                Toast.makeText(context, "Gagal memuat data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showCarOptionsDialog(car: Car) {
        val options = if (car.isSold)
            arrayOf("Edit", "Hapus", "Tandai TERSEDIA")
        else
            arrayOf("Edit", "Hapus", "Tandai SOLD OUT")

        AlertDialog.Builder(requireContext())
            .setTitle(car.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startEditCarActivity(car)
                    1 -> deleteCar(car)
                    2 -> toggleSoldStatus(car)
                }
            }
            .show()
    }

    private fun startEditCarActivity(car: Car) {
        val intent = Intent(requireContext(), SellCarActivity::class.java)
        intent.putExtra("EDIT_CAR_ID", car.documentId)
        addCarLauncher.launch(intent)
    }

    private fun deleteCar(car: Car) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Mobil")
            .setMessage("Yakin ingin menghapus ${car.name}?")
            .setPositiveButton("Hapus") { _, _ ->
                db.collection("cars").document(car.documentId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Mobil berhasil dihapus", Toast.LENGTH_SHORT).show()
                        deleteTransaction(car.documentId, false)
                        loadMyCars()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Gagal menghapus", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun toggleSoldStatus(car: Car) {
        val newStatus = !car.isSold

        db.collection("cars").document(car.documentId)
            .update("isSold", newStatus)
            .addOnSuccessListener {

                if (newStatus) {
                    val transactionData = hashMapOf<String, Any?>(
                        "carId" to car.documentId,
                        "carName" to car.name,
                        "sellerId" to auth.currentUser?.uid,
                        "salePrice" to car.price,
                        "soldDate" to com.google.firebase.Timestamp.now(),
                        "transactionStatus" to "COMPLETED",
                        "canceledDate" to null
                    )
                    db.collection("transactions").add(transactionData)
                } else {
                    db.collection("transactions")
                        .whereEqualTo("carId", car.documentId)
                        .whereEqualTo("transactionStatus", "COMPLETED")
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            val batch = db.batch()
                            for (doc in querySnapshot.documents) {
                                batch.update(doc.reference, "transactionStatus", "CANCELED")
                                batch.update(doc.reference, "canceledDate", com.google.firebase.Timestamp.now())
                            }
                            batch.commit()
                        }
                }

                car.isSold = newStatus

                // Sort ulang list agar yang terjual turun ke bawah
                myCarsList.sortBy { it.isSold }

                // Refresh Adapter
                adapter.updateList(myCarsList)

                val msg = if(newStatus) "Mobil ditandai TERJUAL" else "Mobil kembali TERSEDIA"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Gagal update status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Fungsi Transaction
    private fun deleteTransaction(carId: String, enableRollback: Boolean) {
        db.collection("transactions")
            .whereEqualTo("carId", carId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val batch = db.batch()
                for (document in querySnapshot.documents) {
                    batch.delete(document.reference)
                }
                batch.commit()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}