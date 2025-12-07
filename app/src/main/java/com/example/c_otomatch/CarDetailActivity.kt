package com.example.c_otomatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2 // PENTING: Import ini
import com.bumptech.glide.Glide
import com.example.c_otomatch.adapters.CommentAdapter
import com.example.c_otomatch.adapters.ImageSliderAdapter // PENTING: Adapter Slider
import com.example.c_otomatch.databinding.ActivityCarDetailBinding
import com.example.c_otomatch.models.Car
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

        // --- 1. AMBIL DATA DARI INTENT (Agar Teks Muncul Instan) ---
        carDocumentId = intent.getStringExtra("car_document_id")

        val carName = intent.getStringExtra("car_name").orEmpty()
        val carBrand = intent.getStringExtra("car_brand").orEmpty()
        val carYear = intent.getIntExtra("car_year", 0)
        val carPrice = intent.getStringExtra("car_price").orEmpty()
        // Note: car_image_url dari intent cuma 1 foto (thumbnail), kita pakai ini sementara
        val thumbUrl = intent.getStringExtra("car_image_url").orEmpty()

        val sellerContact = intent.getStringExtra("seller_contact").orEmpty()
        val carLocation = intent.getStringExtra("car_location").orEmpty()
        val sellerName = intent.getStringExtra("seller_name").orEmpty()

        val bodyType = intent.getStringExtra("body_type").orEmpty()
        val color = intent.getStringExtra("color").orEmpty()
        val transmission = intent.getStringExtra("transmission").orEmpty()
        val fuel = intent.getStringExtra("fuel").orEmpty()
        val mileage = intent.getStringExtra("car_mileage").orEmpty()
        val variant = intent.getStringExtra("variant").orEmpty()
        val capacity = intent.getStringExtra("capacity").orEmpty()

        // --- 2. BIND TEXT KE UI ---
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

        // --- 3. SETUP SLIDER GAMBAR (PERBAIKAN UTAMA) ---
        // Kita panggil fungsi khusus untuk setup slider
        // Kita kirim 'thumbUrl' sebagai cadangan jika loading database lama/gagal
        setupImageSlider(thumbUrl)

        // --- 4. LOAD DATA LENGKAP DARI FIRESTORE ---
        // Kenapa load lagi? Karena Intent mungkin tidak membawa LIST semua foto (imageUrls)
        // Kita butuh array 'imageUrls' untuk slider yang lengkap.
        if (carDocumentId != null) {
            db.collection("cars").document(carDocumentId!!).get()
                .addOnSuccessListener { document ->
                    val car = document.toObject(Car::class.java)
                    if (car != null) {
                        // Update Slider dengan List Foto Lengkap dari Database
                        updateSliderWithFullData(car)
                    }
                }
        }

        setupComments()
        setupActionButtons(sellerContact, carName, carYear, carPrice, carLocation)
    } // <--- KURUNG TUTUP onCreate (Disini letak kesalahan sebelumnya)

    // --- FUNGSI-FUNGSI DI BAWAH INI HARUS DI LUAR onCreate ---

    // Fungsi Setup Slider Awal (Pakai 1 foto dari Intent dulu biar gak kosong)
    private fun setupImageSlider(thumbnailUrl: String) {
        val initialList = if (thumbnailUrl.isNotEmpty()) listOf(thumbnailUrl) else emptyList()
        val adapter = ImageSliderAdapter(initialList)
        binding.vpDetailImages.adapter = adapter

        if (initialList.isNotEmpty()) {
            binding.tvImageCount.text = "1/1"
        } else {
            binding.tvImageCount.text = "0/0"
        }
    }

    // Fungsi Update Slider (Setelah data lengkap dari Firestore didapat)
    private fun updateSliderWithFullData(car: Car) {
        // Prioritas: Pakai imageUrls (banyak), kalau kosong pakai imageUrl (satu)
        val images = if (car.imageUrls.isNotEmpty()) car.imageUrls else listOf(car.imageUrl)

        // Cek validitas list
        if (images.isNotEmpty() && images[0].isNotEmpty()) {
            val adapter = ImageSliderAdapter(images)
            binding.vpDetailImages.adapter = adapter

            // Set text indikator awal
            binding.tvImageCount.text = "1/${images.size}"

            // Listener untuk update indikator (contoh: 1/5 -> 2/5) saat digeser
            binding.vpDetailImages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    binding.tvImageCount.text = "${position + 1}/${images.size}"
                }
            })
        }
    }

    private fun setupActionButtons(contact: String, name: String, year: Int, price: String, location: String) {
        binding.btnContactSeller.setOnClickListener {
            if (contact.isNotEmpty()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contact")))
            } else {
                Toast.makeText(this, "Nomor tidak tersedia", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnWhatsapp.setOnClickListener {
            if (contact.isNotEmpty()) {
                var phone = contact
                if (phone.startsWith("0")) phone = "62" + phone.substring(1)

                val message = "Halo, saya tertarik dengan mobil *$name ($year)* seharga *$price* di OtoMatch."
                val url = "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}"

                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this, "WhatsApp error", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Cek mobil $name seharga $price di lokasi $location via OtoMatch!"
                )
            }
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