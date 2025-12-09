package com.example.c_otomatch.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.c_otomatch.R
import com.example.c_otomatch.models.Car
import java.text.NumberFormat
import java.util.*

class MatchAdapter(
    private var cars: List<Car>
) : RecyclerView.Adapter<MatchAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgCard: ImageView = view.findViewById(R.id.imgCard)
        val name: TextView = view.findViewById(R.id.tvCardName)
        val price: TextView = view.findViewById(R.id.tvCardPrice)
        val details: TextView = view.findViewById(R.id.tvCardDetails)
        val location: TextView = view.findViewById(R.id.tvCardLocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(inflater.inflate(R.layout.item_match_card, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val car = cars[position]

        // Load Gambar
        val imgUrl = if (car.imageUrls.isNotEmpty()) car.imageUrls[0] else car.imageUrl
        Glide.with(holder.itemView.context)
            .load(imgUrl)
            .placeholder(R.drawable.ic_car)
            .centerCrop()
            .into(holder.imgCard)

        holder.name.text = car.name
        holder.price.text = formatPrice(car.price)
        holder.location.text = "📍 ${car.location}"

        // Format detail: "2020 • SUV • Bensin"
        val detailText = listOf(car.year.toString(), car.bodyType, car.fuel)
            .filter { it.isNotEmpty() && it != "0" }
            .joinToString(" • ")
        holder.details.text = detailText
    }

    override fun getItemCount(): Int = cars.size

    private fun formatPrice(price: String): String {
        if (price.contains("Rp")) return price
        return try {
            val value = price.replace(Regex("[^0-9]"), "").toLong()
            val formatted = NumberFormat.getNumberInstance(Locale("id", "ID")).format(value)
            "Rp $formatted"
        } catch (e: Exception) { price }
    }

    fun getCarAt(position: Int): Car? {
        return if (position in cars.indices) cars[position] else null
    }
}