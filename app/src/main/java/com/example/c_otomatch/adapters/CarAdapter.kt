package com.example.c_otomatch.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.c_otomatch.R
import com.example.c_otomatch.models.Car
import java.text.NumberFormat
import android.util.Log
import com.google.firebase.firestore.FieldValue
import java.util.*

class CarAdapter(
    private var carList: List<Car>,
    private val onItemClicked: (Car) -> Unit,
    private val onMarkSoldClicked: (Car) -> Unit,
    private val isSellFragment: Boolean = false
) : RecyclerView.Adapter<CarAdapter.CarViewHolder>() {

    inner class CarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgCar: ImageView = itemView.findViewById(R.id.imgCar)
        val tvCarName: TextView = itemView.findViewById(R.id.tvCarName)
        val tvCarBrand: TextView = itemView.findViewById(R.id.tvCarBrand)
        val tvCarPrice: TextView = itemView.findViewById(R.id.tvCarPrice)
        val btnFavorite: ImageView = itemView.findViewById(R.id.btnFavorite)
        val tvSoldLabel: TextView = itemView.findViewById(R.id.tvSoldLabel)
        val btnMarkSold: TextView = itemView.findViewById(R.id.btnMarkSold)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_car, parent, false)
        return CarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CarViewHolder, position: Int) {
        val car = carList[position]

        Glide.with(holder.itemView.context)
            .load(car.imageUrl) // dari URL
            .placeholder(R.drawable.ic_car) // Gambar default saat loading
            .error(R.drawable.ic_car) // Gambar default kalo error
            .into(holder.imgCar)

        holder.tvCarName.text = car.name
        holder.tvCarBrand.text = car.brand
        holder.tvCarPrice.text = formatPrice(car.price)

        holder.btnFavorite.setImageResource(
            if (car.isWishlist) R.drawable.ic_wishlist else R.drawable.ic_wishlist_border
        )
        holder.btnFavorite.setOnClickListener {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val user = auth.currentUser
            if (user == null) {
                Toast.makeText(holder.itemView.context, "Anda harus login untuk menambah wishlist", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            animateButton(holder.btnFavorite)
            car.isWishlist = !car.isWishlist
            holder.btnFavorite.setImageResource(
                if (car.isWishlist) R.drawable.ic_wishlist else R.drawable.ic_wishlist_border
            )
            val userDocRef = db.collection("users").document(user.uid)
            val carId = car.documentId
            if (carId.isBlank()) {
                Log.e("CarAdapter", "Car documentId is blank. Cannot update wishlist.")
                return@setOnClickListener
            }
            if (car.isWishlist) {
                userDocRef.update("wishlist", FieldValue.arrayUnion(carId))
                    .addOnSuccessListener {
                        Toast.makeText(holder.itemView.context, "Ditambahkan ke wishlist", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e("CarAdapter", "Error adding to wishlist", e)
                        car.isWishlist = false
                        holder.btnFavorite.setImageResource(R.drawable.ic_wishlist_border)
                    }
            } else {
                userDocRef.update("wishlist", FieldValue.arrayRemove(carId))
                    .addOnSuccessListener {
                        Toast.makeText(holder.itemView.context, "Dihapus dari wishlist", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e("CarAdapter", "Error removing from wishlist", e)
                        car.isWishlist = true
                        holder.btnFavorite.setImageResource(R.drawable.ic_wishlist)
                    }
            }
        }

        holder.tvSoldLabel.visibility = if (car.isSold) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onItemClicked(car) }

        if (isSellFragment) {
            holder.btnMarkSold.visibility = View.VISIBLE
            // Atur tampilan tombol HANYA berdasarkan data dari Firestore
            holder.btnMarkSold.text = if (car.isSold) "SOLD" else "Mark as SOLD"
            holder.btnMarkSold.alpha = if (car.isSold) 0.6f else 1f

            holder.btnMarkSold.setOnClickListener {
                onMarkSoldClicked(car) // Laporkan klik ini ke Fragment
            }
        } else {
            holder.btnMarkSold.visibility = View.GONE
        }
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