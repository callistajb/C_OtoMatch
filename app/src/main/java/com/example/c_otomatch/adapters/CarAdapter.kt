package com.example.c_otomatch.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
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
    private val onCompareChecked: ((Car, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<CarAdapter.CarViewHolder>() {

    // List untuk menyimpan ID mobil yang sedang dibandingkan
    private val selectedForComparison = mutableListOf<String>()

    // List untuk menyimpan ID mobil yang ada di wishlist user (Sinkronisasi Home)
    private val userWishlistIds = mutableListOf<String>()

    // Fungsi untuk update data wishlist dari Fragment ke Adapter
    fun updateWishlist(newWishlistIds: List<String>) {
        userWishlistIds.clear()
        userWishlistIds.addAll(newWishlistIds)
        notifyDataSetChanged() // Refresh UI agar hati merah/putih sesuai
    }

    inner class CarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgCar: ImageView = itemView.findViewById(R.id.imgCar)
        val tvCarName: TextView = itemView.findViewById(R.id.tvCarName)
        val tvCarBrand: TextView = itemView.findViewById(R.id.tvCarBrand)
        val tvCarPrice: TextView = itemView.findViewById(R.id.tvCarPrice)
        val btnFavorite: ImageView = itemView.findViewById(R.id.btnFavorite)
        val tvSoldLabel: TextView = itemView.findViewById(R.id.tvSoldLabel)
        val btnMarkSold: TextView = itemView.findViewById(R.id.btnMarkSold)
        val cbCompare: CheckBox = itemView.findViewById(R.id.cbCompare)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_car, parent, false)
        return CarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CarViewHolder, position: Int) {
        val car = carList[position]

        // 1. Load Gambar (Safe Load)
        val imageToLoad = if (car.imageUrls.isNotEmpty()) car.imageUrls[0] else car.imageUrl
        Glide.with(holder.itemView.context)
            .load(imageToLoad)
            .placeholder(R.drawable.ic_car)
            .error(R.drawable.ic_car)
            .override(500, 500)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(holder.imgCar)

        // 2. Set Text
        holder.tvCarName.text = car.name
        holder.tvCarBrand.text = car.brand
        holder.tvCarPrice.text = formatPrice(car.price)

        // 3. Logika Wishlist (SINKRONISASI DIPERBAIKI)
        // Cek apakah ID mobil ini ada di daftar wishlist user
        val isWishlisted = userWishlistIds.contains(car.documentId)
        // Update visual hati berdasarkan data real
        car.isWishlist = isWishlisted

        holder.btnFavorite.setImageResource(
            if (isWishlisted) R.drawable.ic_wishlist else R.drawable.ic_wishlist_border
        )

        holder.btnFavorite.setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Toast.makeText(holder.itemView.context, "Login dulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            animateButton(holder.btnFavorite)

            // Toggle local state sementara
            val willBeWishlist = !userWishlistIds.contains(car.documentId)

            if (willBeWishlist) {
                userWishlistIds.add(car.documentId)
                holder.btnFavorite.setImageResource(R.drawable.ic_wishlist)
            } else {
                userWishlistIds.remove(car.documentId)
                holder.btnFavorite.setImageResource(R.drawable.ic_wishlist_border)
            }

            // Update Database
            val db = FirebaseFirestore.getInstance()
            val userDocRef = db.collection("users").document(user.uid)

            if (car.documentId.isNotEmpty()) {
                if (willBeWishlist) {
                    userDocRef.update("wishlist", FieldValue.arrayUnion(car.documentId))
                } else {
                    userDocRef.update("wishlist", FieldValue.arrayRemove(car.documentId))
                }
            }
        }

        // 4. Label SOLD OUT (Untuk Home)
        holder.tvSoldLabel.visibility = if (car.isSold) View.VISIBLE else View.GONE

        // Klik Item Mobil -> Buka Detail
        holder.itemView.setOnClickListener { onItemClicked(car) }

        // 5. Logika Khusus per Fragment (Jual vs Home)
        if (isSellFragment) {
            // --- TAMPILAN DI HALAMAN JUAL ---
            holder.btnMarkSold.visibility = View.VISIBLE
            holder.cbCompare.visibility = View.GONE

            // Logika text tombol Mark Sold
            if (car.isSold) {
                holder.btnMarkSold.text = "TERJUAL"
                holder.btnMarkSold.alpha = 0.5f // Agak transparan kalau terjual
            } else {
                holder.btnMarkSold.text = "Tandai TERJUAL"
                holder.btnMarkSold.alpha = 1f
            }

            holder.btnMarkSold.setOnClickListener {
                onMarkSoldClicked(car)
            }
        } else {
            // --- TAMPILAN DI HALAMAN HOME ---
            holder.btnMarkSold.visibility = View.GONE
            holder.cbCompare.visibility = View.VISIBLE

            holder.cbCompare.setOnCheckedChangeListener(null)
            holder.cbCompare.isChecked = selectedForComparison.contains(car.documentId)

            holder.cbCompare.setOnCheckedChangeListener { buttonView, isChecked ->
                if (buttonView.isPressed) {
                    if (isChecked) {
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
    }

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
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply { duration = 150 }
        view.startAnimation(anim)
    }
}