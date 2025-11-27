package com.example.c_otomatch.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.c_otomatch.R
import com.example.c_otomatch.models.Car
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.*

class CarAdapter(
    private var carList: List<Car>,
    private val onItemClicked: (Car) -> Unit,
    private val onMarkSoldClicked: (Car) -> Unit,
    private val isSellFragment: Boolean = false,
    // Callback khusus untuk fitur bandingkan (opsional, default null)
    private val onCompareChecked: ((Car, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<CarAdapter.CarViewHolder>() {

    // List sementara untuk menyimpan ID mobil yang sedang dicentang (biar ga hilang pas scroll)
    private val selectedForComparison = mutableListOf<String>()

    inner class CarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgCar: ImageView = itemView.findViewById(R.id.imgCar)
        val tvCarName: TextView = itemView.findViewById(R.id.tvCarName)
        val tvCarBrand: TextView = itemView.findViewById(R.id.tvCarBrand)
        val tvCarPrice: TextView = itemView.findViewById(R.id.tvCarPrice)
        val btnFavorite: ImageView = itemView.findViewById(R.id.btnFavorite)
        val tvSoldLabel: TextView = itemView.findViewById(R.id.tvSoldLabel)
        val btnMarkSold: TextView = itemView.findViewById(R.id.btnMarkSold)
        // Checkbox untuk fitur bandingkan
        val cbCompare: CheckBox = itemView.findViewById(R.id.cbCompare)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_car, parent, false)
        return CarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CarViewHolder, position: Int) {
        val car = carList[position]

        // 1. Load Gambar dengan Glide
        Glide.with(holder.itemView.context)
            .load(car.imageUrl)
            .placeholder(R.drawable.ic_car) // Gambar loading
            .error(R.drawable.ic_car)       // Gambar error
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Simpan cache biar cepet
            .into(holder.imgCar)

        // 2. Set Data Teks
        holder.tvCarName.text = car.name
        holder.tvCarBrand.text = car.brand
        holder.tvCarPrice.text = formatPrice(car.price)

        // 3. Logika Wishlist (Love)
        holder.btnFavorite.setImageResource(
            if (car.isWishlist) R.drawable.ic_wishlist else R.drawable.ic_wishlist_border
        )

        holder.btnFavorite.setOnClickListener {
            val auth = FirebaseAuth.getInstance()
            val db = FirebaseFirestore.getInstance()
            val user = auth.currentUser

            if (user == null) {
                Toast.makeText(holder.itemView.context, "Login dulu bos!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            animateButton(holder.btnFavorite)

            // Ubah UI langsung (Optimistic Update)
            car.isWishlist = !car.isWishlist
            holder.btnFavorite.setImageResource(
                if (car.isWishlist) R.drawable.ic_wishlist else R.drawable.ic_wishlist_border
            )

            // Update ke Firestore
            val userDocRef = db.collection("users").document(user.uid)
            if (car.documentId.isNotBlank()) {
                if (car.isWishlist) {
                    userDocRef.update("wishlist", FieldValue.arrayUnion(car.documentId))
                } else {
                    userDocRef.update("wishlist", FieldValue.arrayRemove(car.documentId))
                }
            }
        }

        // 4. Label SOLD OUT
        holder.tvSoldLabel.visibility = if (car.isSold) View.VISIBLE else View.GONE

        // Klik Item Mobil -> Buka Detail
        holder.itemView.setOnClickListener { onItemClicked(car) }

        // 5. Logika Khusus per Fragment (Jual vs Home)
        if (isSellFragment) {
            // --- TAMPILAN DI HALAMAN JUAL ---
            holder.btnMarkSold.visibility = View.VISIBLE
            holder.cbCompare.visibility = View.GONE // Sembunyikan checkbox di menu jual

            holder.btnMarkSold.text = if (car.isSold) "TERJUAL" else "Tandai TERJUAL"
            holder.btnMarkSold.alpha = if (car.isSold) 0.6f else 1f

            holder.btnMarkSold.setOnClickListener {
                onMarkSoldClicked(car)
            }

        } else {
            // --- TAMPILAN DI HALAMAN HOME ---
            holder.btnMarkSold.visibility = View.GONE
            holder.cbCompare.visibility = View.VISIBLE // Tampilkan checkbox

            // Hapus listener lama dulu biar ga konflik saat scrolling (Recycling issue)
            holder.cbCompare.setOnCheckedChangeListener(null)

            // Set status checkbox berdasarkan list 'selectedForComparison'
            holder.cbCompare.isChecked = selectedForComparison.contains(car.documentId)

            // Pasang listener baru
            holder.cbCompare.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Batasi maksimal 2 mobil
                    if (selectedForComparison.size >= 2) {
                        Toast.makeText(holder.itemView.context, "Maksimal 2 mobil!", Toast.LENGTH_SHORT).show()
                        holder.cbCompare.isChecked = false
                    } else {
                        if (!selectedForComparison.contains(car.documentId)) {
                            selectedForComparison.add(car.documentId)
                            onCompareChecked?.invoke(car, true)
                        }
                    }
                } else {
                    selectedForComparison.remove(car.documentId)
                    onCompareChecked?.invoke(car, false)
                }
            }
        }
    }

    // Helper untuk mereset checkbox setelah selesai membandingkan
    fun clearSelection() {
        selectedForComparison.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = carList.size

    fun updateList(newList: List<Car>) {
        carList = newList
        notifyDataSetChanged()
    }

    private fun formatPrice(price: String): String {
        if (price.contains("Rp", ignoreCase = true)) return price
        return try {
            val value = price.replace(Regex("[^0-9]"), "").toLong()
            val formatted = NumberFormat.getNumberInstance(Locale("id", "ID")).format(value)
            "Rp $formatted"
        } catch (e: Exception) {
            price
        }
    }

    private fun animateButton(view: View) {
        val anim = ScaleAnimation(
            0.8f, 1f, 0.8f, 1f,
            (view.width / 2).toFloat(),
            (view.height / 2).toFloat()
        ).apply { duration = 150 }
        view.startAnimation(anim)
    }
}