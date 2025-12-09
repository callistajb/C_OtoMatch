package com.example.c_otomatch.adapters

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
    private val isSellFragment: Boolean = false
) : RecyclerView.Adapter<CarAdapter.CarViewHolder>() {

    private val userWishlistIds = mutableListOf<String>()

    fun updateWishlist(newWishlistIds: List<String>) {
        userWishlistIds.clear()
        userWishlistIds.addAll(newWishlistIds)
        notifyDataSetChanged()
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
        val imageToLoad = if (car.imageUrls.isNotEmpty()) car.imageUrls[0] else car.imageUrl
        Glide.with(holder.itemView.context)
            .load(imageToLoad)
            .placeholder(R.drawable.ic_car)
            .error(R.drawable.ic_car)
            .override(500, 500)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(holder.imgCar)

        holder.tvCarName.text = car.name
        holder.tvCarBrand.text = car.brand
        holder.tvCarPrice.text = formatPrice(car.price)

        val isWishlisted = userWishlistIds.contains(car.documentId)
        car.isWishlist = isWishlisted

        holder.btnFavorite.setImageResource(
            if (isWishlisted) R.drawable.ic_wishlist else R.drawable.ic_wishlist_border
        )

        holder.btnFavorite.setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                // --- BAHASA LEBIH ELEGAN ---
                Toast.makeText(holder.itemView.context, "Silakan login untuk menyimpan ke Wishlist.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            animateButton(holder.btnFavorite)
            val willBeWishlist = !userWishlistIds.contains(car.documentId)

            if (willBeWishlist) {
                userWishlistIds.add(car.documentId)
                holder.btnFavorite.setImageResource(R.drawable.ic_wishlist)
            } else {
                userWishlistIds.remove(car.documentId)
                holder.btnFavorite.setImageResource(R.drawable.ic_wishlist_border)
            }

            val db = FirebaseFirestore.getInstance()
            val userDocRef = db.collection("users").document(user.uid)

            if (car.documentId.isNotEmpty()) {
                if (willBeWishlist) userDocRef.update("wishlist", FieldValue.arrayUnion(car.documentId))
                else userDocRef.update("wishlist", FieldValue.arrayRemove(car.documentId))
            }
        }

        holder.tvSoldLabel.visibility = if (car.isSold) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onItemClicked(car) }

        holder.cbCompare.visibility = View.GONE

        if (isSellFragment) {
            holder.btnMarkSold.visibility = View.VISIBLE
            if (car.isSold) {
                holder.btnMarkSold.text = "TERJUAL"
                holder.btnMarkSold.alpha = 0.5f
            } else {
                holder.btnMarkSold.text = "Tandai TERJUAL"
                holder.btnMarkSold.alpha = 1.0f
            }
            holder.btnMarkSold.setOnClickListener { onMarkSoldClicked(car) }
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
        } catch (e: Exception) { price }
    }

    private fun animateButton(view: View) {
        val anim = ScaleAnimation(0.8f, 1f, 0.8f, 1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f)
        anim.duration = 150
        view.startAnimation(anim)
    }
}