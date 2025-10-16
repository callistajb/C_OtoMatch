package com.example.c_otomatch.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.c_otomatch.R
import com.example.c_otomatch.models.Car

class CarAdapter(
    private val carList: List<Car>,
    private val onItemClicked: (Car) -> Unit
) : RecyclerView.Adapter<CarAdapter.CarViewHolder>() {

    inner class CarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgCar: ImageView = itemView.findViewById(R.id.imgCar)
        val tvCarName: TextView = itemView.findViewById(R.id.tvCarName)
        val tvCarBrand: TextView = itemView.findViewById(R.id.tvCarBrand)
        val tvCarPrice: TextView = itemView.findViewById(R.id.tvCarPrice)
        val btnFavorite: ImageView = itemView.findViewById(R.id.btnFavorite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_car, parent, false)
        return CarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CarViewHolder, position: Int) {
        val car = carList[position]
        holder.imgCar.setImageResource(car.imageResId)
        holder.tvCarName.text = car.name
        holder.tvCarBrand.text = car.brand
        holder.tvCarPrice.text = car.price

        // Set icon awal sesuai status wishlist
        holder.btnFavorite.setImageResource(
            if (car.isWishlist) R.drawable.ic_wishlist else R.drawable.ic_wishlist_border
        )

        // Klik item → buka detail (kalau ada callback)
        holder.itemView.setOnClickListener {
            onItemClicked(car)
        }

        // Klik tombol hati → ubah status
        holder.btnFavorite.setOnClickListener {
            car.isWishlist = !car.isWishlist

            // Ganti icon-nya langsung
            holder.btnFavorite.setImageResource(
                if (car.isWishlist) R.drawable.ic_wishlist else R.drawable.ic_wishlist_border
            )

            // Feedback Toast
            Toast.makeText(
                holder.itemView.context,
                if (car.isWishlist) "Ditambahkan ke wishlist" else "Dihapus dari wishlist",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun getItemCount() = carList.size
}
