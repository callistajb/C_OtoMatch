package com.example.c_otomatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbarCarDetail.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Ambil Data
        carDocumentId = intent.getStringExtra("car_document_id")
        val carName = intent.getStringExtra("car_name").orEmpty()
        val carBrand = intent.getStringExtra("car_brand").orEmpty()
        val carYear = intent.getIntExtra("car_year", 0)
        val carPrice = intent.getStringExtra("car_price").orEmpty()
        val carImageUrl = intent.getStringExtra("car_image_url").orEmpty()
        val sellerContact = intent.getStringExtra("seller_contact").orEmpty()
        val carLocation = intent.getStringExtra("car_location").orEmpty()
        val sellerName = intent.getStringExtra("seller_name").orEmpty()

        // Spek Detail
        val bodyType = intent.getStringExtra("body_type").orEmpty()
        val color = intent.getStringExtra("color").orEmpty()
        val transmission = intent.getStringExtra("transmission").orEmpty()
        val fuel = intent.getStringExtra("fuel").orEmpty()
        val mileage = intent.getStringExtra("car_mileage").orEmpty()
        val variant = intent.getStringExtra("variant").orEmpty()
        val capacity = intent.getStringExtra("capacity").orEmpty()

        // Load Gambar
        Glide.with(this)
            .load(carImageUrl)
            .placeholder(R.drawable.ic_car)
            .error(R.drawable.ic_car)
            .into(binding.imgCarDetail)

        // Bind Text
        binding.apply {
            tvCarNameDetail.text = carName
            tvCarBrandDetail.text = carBrand
            tvCarYearDetail.text = "Tahun: $carYear"
            tvCarPriceBadge.text = carPrice
            tvCarLocationDetail.text = "Lokasi: $carLocation"
            tvSellerDetail.text = "Penjual: $sellerName"
            tvContactDetail.text = sellerContact.ifEmpty { "Tidak tersedia" }

            tvVariantDetail.text = "Varian: ${variant.ifEmpty { "-" }}"
            tvBodyDetail.text = "Tipe: ${bodyType.ifEmpty { "-" }}"
            tvColorDetail.text = "Warna: ${color.ifEmpty { "-" }}"
            tvTransmissionDetail.text = "Transmisi: ${transmission.ifEmpty { "-" }}"
            tvFuelDetail.text = "Bahan Bakar: ${fuel.ifEmpty { "-" }}"
            tvKmRangeDetail.text = "Jarak Tempuh: ${mileage.ifEmpty { "-" }}"
            tvCapacityDetail.text = "Kapasitas Mesin: ${capacity.ifEmpty { "-" }}"
        }

        setupComments()

        // --- FITUR WA & SHARE ---

        // 1. Tombol Kontak (Telp Biasa)
        binding.btnContactSeller.setOnClickListener {
            if (sellerContact.isNotEmpty()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sellerContact")))
            } else {
                Toast.makeText(this, "Nomor tidak tersedia", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Tombol Beli via WA (Checkout)
        binding.btnWhatsapp.setOnClickListener {
            if (sellerContact.isNotEmpty()) {
                var phone = sellerContact
                // Ubah 08xxx jadi 628xxx
                if (phone.startsWith("0")) phone = "62" + phone.substring(1)

                val message = "Halo, saya tertarik dengan mobil *$carName ($carYear)* seharga *$carPrice* yang ada di OtoMatch. Masih ada?"
                val url = "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}"

                try {
                    val i = Intent(Intent.ACTION_VIEW)
                    i.data = Uri.parse(url)
                    startActivity(i)
                } catch (e: Exception) {
                    Toast.makeText(this, "WhatsApp belum terinstall", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 3. Tombol Share
        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            val body = "Cek mobil ini di OtoMatch!\n\n$carName ($carYear)\nHarga: $carPrice\nLokasi: $carLocation"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Jual $carName")
            shareIntent.putExtra(Intent.EXTRA_TEXT, body)
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }
    }

    private fun setupComments() {
        commentAdapter = CommentAdapter(commentList)
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(this@CarDetailActivity)
            adapter = commentAdapter
            setHasFixedSize(true)
        }

        if (carDocumentId != null) loadComments(carDocumentId!!)

        binding.btnSubmitComment.setOnClickListener {
            val text = binding.etCommentInput.text.toString().trim()
            val rating = binding.ratingBarInput.rating

            if (auth.currentUser == null) {
                Toast.makeText(this, "Login dulu bro", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (text.isEmpty()) {
                binding.etCommentInput.error = "Isi dulu komennya"
                return@setOnClickListener
            }

            db.collection("users").document(auth.currentUser!!.uid).get().addOnSuccessListener {
                val name = it.getString("name") ?: "User"
                val comment = Comment(name, text, rating, auth.currentUser!!.uid)

                db.collection("cars").document(carDocumentId!!).collection("comments").add(comment)
                    .addOnSuccessListener {
                        binding.etCommentInput.setText("")
                        binding.ratingBarInput.rating = 0f
                        commentList.add(0, comment)
                        commentAdapter.notifyItemInserted(0)
                        binding.rvComments.scrollToPosition(0)
                    }
            }
        }
    }

    private fun loadComments(carId: String) {
        db.collection("cars").document(carId).collection("comments")
            .orderBy("rating", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                commentList.clear()
                for (doc in result) {
                    commentList.add(doc.toObject(Comment::class.java))
                }
                commentAdapter.notifyDataSetChanged()
                updateAvgRating()
            }
    }

    private fun updateAvgRating() {
        if (commentList.isNotEmpty()) {
            val avg = commentList.map { it.rating }.average()
            binding.tvAvgRating.text = "Rating: %.1f ⭐".format(avg)
        }
    }
}