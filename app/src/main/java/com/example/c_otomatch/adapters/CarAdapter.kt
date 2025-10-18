package com.example.c_otomatch.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.c_otomatch.R
import com.example.c_otomatch.models.Car
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.util.*

class CarAdapter(
    private var carList: List<Car>,
    private val onItemClicked: (Car) -> Unit,
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

        // --- Set data dasar ---
        holder.imgCar.setImageResource(car.imageResId)
        holder.tvCarName.text = car.name
        holder.tvCarBrand.text = car.brand
        holder.tvCarPrice.text = formatPrice(car.price)

        // --- Wishlist button ---
        holder.btnFavorite.setImageResource(
            if (car.isWishlist) R.drawable.ic_wishlist else R.drawable.ic_wishlist_border
        )

        holder.btnFavorite.setOnClickListener {
            animateButton(holder.btnFavorite)
            car.isWishlist = !car.isWishlist
            holder.btnFavorite.setImageResource(
                if (car.isWishlist) R.drawable.ic_wishlist else R.drawable.ic_wishlist_border
            )
            Toast.makeText(
                holder.itemView.context,
                if (car.isWishlist) "Ditambahkan ke wishlist" else "Dihapus dari wishlist",
                Toast.LENGTH_SHORT
            ).show()
        }

        // --- Label SOLD ---
        holder.tvSoldLabel.visibility = if (car.isSold) View.VISIBLE else View.GONE

        // --- Klik item (buka detail / action lain) ---
        holder.itemView.setOnClickListener { onItemClicked(car) }

        // --- Tombol "Mark as SOLD" hanya muncul di SellFragment ---
        if (isSellFragment) {
            holder.btnMarkSold.visibility = View.VISIBLE
            holder.btnMarkSold.text = if (car.isSold) "SOLD" else "Mark as SOLD"
            holder.btnMarkSold.alpha = if (car.isSold) 0.6f else 1f

            holder.btnMarkSold.setOnClickListener {
                car.isSold = !car.isSold // toggle

                // ubah tampilan sesuai status
                holder.btnMarkSold.text = if (car.isSold) "SOLD" else "Mark as SOLD"
                holder.btnMarkSold.alpha = if (car.isSold) 0.6f else 1f

                // label “SOLD” di pojok kanan atas
                holder.tvSoldLabel.visibility = if (car.isSold) View.VISIBLE else View.GONE

                // tampilkan feedback Snackbar
                Snackbar.make(
                    holder.itemView,
                    if (car.isSold)
                        "${car.name} ditandai sebagai SOLD"
                    else
                        "${car.name} dikembalikan menjadi tersedia",
                    Snackbar.LENGTH_SHORT
                ).show()

                // refresh tampilan item (tanpa disable klik)
                notifyItemChanged(position)
            }
        } else {
            // Selain SellFragment, tombol ini disembunyikan
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
