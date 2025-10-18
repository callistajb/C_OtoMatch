package com.example.c_otomatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.content.ContextCompat
import com.example.c_otomatch.adapters.CommentAdapter
import com.example.c_otomatch.databinding.ActivityCarDetailBinding
import com.example.c_otomatch.models.Comment

class CarDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCarDetailBinding
    private lateinit var commentAdapter: CommentAdapter
    private val commentList = mutableListOf<Comment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCarDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarCarDetail)
        supportActionBar?.apply {
            title = "Detail Mobil"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.toolbarCarDetail.navigationIcon =
            ContextCompat.getDrawable(this, androidx.appcompat.R.drawable.abc_ic_ab_back_material)

        binding.toolbarCarDetail.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }


        // Ambil data dari intent
        val carName = intent.getStringExtra("car_name").orEmpty()
        val carBrand = intent.getStringExtra("car_brand").orEmpty()
        val carYear = intent.getIntExtra("car_year", 0)
        val carPrice = intent.getStringExtra("car_price").orEmpty()
        val carMileage = intent.getStringExtra("car_mileage").orEmpty()
        val carLocation = intent.getStringExtra("car_location").orEmpty()
        val carImageResId = intent.getIntExtra("car_image", 0)
        val sellerName = intent.getStringExtra("seller_name").orEmpty()
        val sellerContact = intent.getStringExtra("seller_contact").orEmpty()
        val bodyType = intent.getStringExtra("body_type").orEmpty()
        val color = intent.getStringExtra("color").orEmpty()
        val transmission = intent.getStringExtra("transmission").orEmpty()
        val fuel = intent.getStringExtra("fuel").orEmpty()
        val kmRange = intent.getStringExtra("km_range").orEmpty()

        // Tampilkan data ke UI
        if (carImageResId != 0) binding.imgCarDetail.setImageResource(carImageResId)
        binding.apply {
            tvCarNameDetail.text = carName
            tvCarBrandDetail.text = carBrand
            tvCarYearDetail.text = "Tahun: $carYear"
            tvCarPriceBadge.text = carPrice
            tvCarLocationDetail.text = "Lokasi: $carLocation"
            tvSellerDetail.text = "Penjual: $sellerName"
            tvContactDetail.text = sellerContact.ifEmpty { "Tidak tersedia" }
            tvBodyDetail.text = "Tipe: ${bodyType.ifEmpty { "-" }}"
            tvColorDetail.text = "Warna: ${color.ifEmpty { "-" }}"
            tvTransmissionDetail.text = "Transmisi: ${transmission.ifEmpty { "-" }}"
            tvFuelDetail.text = "Bahan Bakar: ${fuel.ifEmpty { "-" }}"
            tvKmRangeDetail.text = "Jarak Tempuh: ${kmRange.ifEmpty { "-" }}"
        }

        // Setup RecyclerView untuk komentar
        commentAdapter = CommentAdapter(commentList)
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(this@CarDetailActivity)
            adapter = commentAdapter
            setHasFixedSize(true)
        }

        // Komentar dummy
        addComment(Comment("Budi", "Harga mobilnya cocok sama speknya", 5f))
        addComment(Comment("Dina", "Untuk km segitu harusnya harganya ga segitu", 3f))
        addComment(Comment("Rafi", "Modifikasinya banyak harusnya lebih mahal harganya", 4f))

        // Tombol kirim komentar
        binding.btnSubmitComment.setOnClickListener {
            val commentText = binding.etCommentInput.text.toString().trim()
            val rating = binding.ratingBarInput.rating

            when {
                commentText.isEmpty() -> {
                    binding.etCommentInput.error = "Tulis komentar dulu"
                }
                rating <= 0f -> {
                    Toast.makeText(this, "Beri rating minimal 1 bintang", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    addComment(Comment("Kamu", commentText, rating))
                    binding.etCommentInput.setText("")
                    binding.ratingBarInput.rating = 0f
                }
            }
        }

        // 🔹 Tombol hubungi penjual
        binding.btnContactSeller.setOnClickListener {
            if (sellerContact.isEmpty()) {
                Toast.makeText(this, "Nomor penjual tidak tersedia", Toast.LENGTH_SHORT).show()
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sellerContact"))
                startActivity(dialIntent)
            }
        }
    }

    // Fungsi menambah komentar baru
    private fun addComment(comment: Comment) {
        commentList.add(0, comment)
        commentAdapter.notifyItemInserted(0)
        binding.rvComments.scrollToPosition(0)
        updateAvgRating()
    }

    // Fungsi menghitung rata-rata rating
    private fun updateAvgRating() {
        if (commentList.isEmpty()) {
            binding.tvAvgRating.text = "Rating rata-rata: -"
            return
        }
        val avg = commentList.map { it.rating }.average()
        binding.tvAvgRating.text = "Rating rata-rata: %.1f ⭐".format(avg)
    }
}
