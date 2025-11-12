package com.example.c_otomatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.c_otomatch.adapters.CommentAdapter
import com.example.c_otomatch.databinding.ActivityCarDetailBinding
import com.example.c_otomatch.models.Comment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class CarDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCarDetailBinding
    private lateinit var commentAdapter: CommentAdapter
    private val commentList = mutableListOf<Comment>()

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var carDocumentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCarDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

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

        // Ambil SEMUA data dari intent
        carDocumentId = intent.getStringExtra("car_document_id")
        val carName = intent.getStringExtra("car_name").orEmpty()
        val carBrand = intent.getStringExtra("car_brand").orEmpty()
        val carYear = intent.getIntExtra("car_year", 0)
        val carPrice = intent.getStringExtra("car_price").orEmpty()
        val carImageUrl = intent.getStringExtra("car_image_url").orEmpty()
        val sellerContact = intent.getStringExtra("seller_contact").orEmpty()
        val carLocation = intent.getStringExtra("car_location").orEmpty()
        val sellerName = intent.getStringExtra("seller_name").orEmpty()
        val bodyType = intent.getStringExtra("body_type").orEmpty()
        val color = intent.getStringExtra("color").orEmpty()
        val transmission = intent.getStringExtra("transmission").orEmpty()
        val fuel = intent.getStringExtra("fuel").orEmpty()
        val mileage = intent.getStringExtra("mileage").orEmpty() // Pake mileage
        val variant = intent.getStringExtra("variant").orEmpty()
        val capacity = intent.getStringExtra("capacity").orEmpty()
        // Kamu juga bisa tambahin 'negatives' dan 'mods' kalo mau ditampilin

        // Tampilkan gambar pakai Glide
        Glide.with(this)
            .load(carImageUrl)
            .placeholder(R.drawable.ic_car)
            .error(R.drawable.ic_car)
            .into(binding.imgCarDetail)

        // Tampilkan semua data ke UI
        binding.apply {
            tvCarNameDetail.text = carName
            tvCarBrandDetail.text = carBrand
            tvCarYearDetail.text = "Tahun: $carYear"
            tvCarPriceBadge.text = carPrice
            tvCarLocationDetail.text = "Lokasi: $carLocation"
            tvSellerDetail.text = "Penjual: $sellerName"
            tvContactDetail.text = sellerContact.ifEmpty { "Tidak tersedia" }

            // Spek Detail (udah diupdate)
            tvVariantDetail.text = "Varian: ${variant.ifEmpty { "-" }}"
            tvBodyDetail.text = "Tipe: ${bodyType.ifEmpty { "-" }}"
            tvColorDetail.text = "Warna: ${color.ifEmpty { "-" }}"
            tvTransmissionDetail.text = "Transmisi: ${transmission.ifEmpty { "-" }}"
            tvFuelDetail.text = "Bahan Bakar: ${fuel.ifEmpty { "-" }}"
            tvKmRangeDetail.text = "Jarak Tempuh: ${mileage.ifEmpty { "-" }}" // Ganti ke mileage
            tvCapacityDetail.text = "Kapasitas Mesin: ${capacity.ifEmpty { "-" }}"
        }

        // Setup RecyclerView untuk komentar
        commentAdapter = CommentAdapter(commentList)
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(this@CarDetailActivity)
            adapter = commentAdapter
            setHasFixedSize(true)
        }

        // Load komentar dari Firestore
        if (carDocumentId != null) {
            loadComments(carDocumentId!!)
        }

        // Tombol kirim komentar
        binding.btnSubmitComment.setOnClickListener {
            val commentText = binding.etCommentInput.text.toString().trim()
            val rating = binding.ratingBarInput.rating

            if (carDocumentId == null) {
                Toast.makeText(this, "Error: ID mobil tidak ditemukan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (auth.currentUser == null) {
                Toast.makeText(this, "Anda harus login untuk berkomentar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            when {
                commentText.isEmpty() -> {
                    binding.etCommentInput.error = "Tulis komentar dulu"
                }
                rating <= 0f -> {
                    Toast.makeText(this, "Beri rating minimal 1 bintang", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // Dapat nama user dari Firestore
                    db.collection("users").document(auth.currentUser!!.uid).get()
                        .addOnSuccessListener {
                            // Pake 'username' aja biar keren
                            val userName = it.getString("username") ?: "Anonim"
                            val newComment = Comment(
                                userName = userName,
                                text = commentText,
                                rating = rating,
                                userId = auth.currentUser!!.uid
                            )
                            // Kirim komentar ke Firestore
                            submitComment(carDocumentId!!, newComment)
                        }
                }
            }
        }

        // Tombol kontak
        binding.btnContactSeller.setOnClickListener {
            if (sellerContact.isEmpty()) {
                Toast.makeText(this, "Nomor penjual tidak tersedia", Toast.LENGTH_SHORT).show()
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sellerContact"))
                startActivity(dialIntent)
            }
        }
    }

    private fun loadComments(carId: String) {
        db.collection("cars").document(carId)
            .collection("comments")
            .orderBy("rating", Query.Direction.DESCENDING) // Urutin komen
            .get()
            .addOnSuccessListener { result ->
                commentList.clear()
                for (document in result) {
                    try {
                        val comment = document.toObject(Comment::class.java)
                        commentList.add(comment)
                    } catch (e: Exception) {
                        Log.e("CarDetail", "Error converting comment", e)
                    }
                }
                commentAdapter.notifyDataSetChanged()
                updateAvgRating()
            }
            .addOnFailureListener {
                Log.w("CarDetail", "Error loading comments", it)
            }
    }

    private fun submitComment(carId: String, comment: Comment) {
        db.collection("cars").document(carId)
            .collection("comments")
            .add(comment)
            .addOnSuccessListener {
                Log.d("CarDetail", "Comment added")
                binding.etCommentInput.setText("")
                binding.ratingBarInput.rating = 0f
                // Tambahin ke list lokal, biar langsung muncul
                addCommentToList(comment)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal mengirim komentar", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addCommentToList(comment: Comment) {
        commentList.add(0, comment) // Tampil di paling atas
        commentAdapter.notifyItemInserted(0)
        binding.rvComments.scrollToPosition(0)
        updateAvgRating()
    }

    private fun updateAvgRating() {
        if (commentList.isEmpty()) {
            binding.tvAvgRating.text = "Rating rata-rata: -"
            return
        }
        val avg = commentList.map { it.rating }.average()
        binding.tvAvgRating.text = "Rating rata-rata: %.1f ⭐".format(avg)
    }
}