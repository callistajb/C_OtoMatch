package com.example.c_otomatch.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.c_otomatch.R

// Menerima MutableList<Any> agar tipe data sinkron dengan Activity
class ImageSliderAdapter(
    private val images: MutableList<Any>,
    private val onLongClick: ((Int) -> Unit)? = null // Callback untuk hapus foto
) : RecyclerView.Adapter<ImageSliderAdapter.SliderViewHolder>() {

    inner class SliderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imgSlider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SliderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_slider, parent, false)

        // Agar gambar memenuhi layout
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return SliderViewHolder(view)
    }

    override fun onBindViewHolder(holder: SliderViewHolder, position: Int) {
        // Load gambar (bisa Uri atau String URL)
        Glide.with(holder.itemView.context)
            .load(images[position])
            .placeholder(R.drawable.ic_car)
            .error(R.drawable.ic_car)
            .centerCrop()
            .into(holder.imageView)

        // Fitur: Tekan lama untuk menghapus (Take Back)
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(position)
            true
        }
    }

    override fun getItemCount(): Int = images.size
}