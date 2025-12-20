package com.example.c_otomatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.c_otomatch.adapters.CommentAdapter
import com.example.c_otomatch.adapters.ImageSliderAdapter
import com.example.c_otomatch.databinding.ActivityCarDetailBinding
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.models.Comment
import com.example.c_otomatch.utils.NumberTextWatcher
import com.google.android.material.textfield.TextInputEditText
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

    private var carPriceLong: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCarDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setSupportActionBar(binding.toolbarCarDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbarCarDetail.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // --- TOMBOL SHARE (BARU) ---
        binding.btnShareHeader.setOnClickListener {
            shareCarListing()
        }

        carDocumentId = intent.getStringExtra("car_document_id")
        val carName = intent.getStringExtra("car_name").orEmpty()
        val carPrice = intent.getStringExtra("car_price").orEmpty()

        carPriceLong = try {
            NumberTextWatcher.cleanDigits(carPrice).toLong()
        } catch (e: Exception) {
            0L
        }

        // --- SET TEXT AWAL ---
        binding.apply {
            tvCarNameDetail.text = carName
            tvCarBrandDetail.text = intent.getStringExtra("car_brand").orEmpty()

            // Highlight Specs (Sekarang Label di Atas, Value di Bawah)
            tvCarYearDetail.text = intent.getIntExtra("car_year", 0).toString()
            tvKmRangeDetail.text = intent.getStringExtra("car_mileage").orEmpty()
            tvTransmissionDetail.text =
                intent.getStringExtra("transmission").orEmpty().ifEmpty { "-" }
            tvFuelDetail.text = intent.getStringExtra("fuel").orEmpty().ifEmpty { "-" }

            // Header Info
            tvCarPriceBadge.text = carPrice
            tvCarLocationDetail.text = intent.getStringExtra("car_location").orEmpty()

            // Detail Table
            tvVariantDetail.text = intent.getStringExtra("variant").orEmpty().ifEmpty { "-" }
            tvBodyDetail.text = intent.getStringExtra("body_type").orEmpty().ifEmpty { "-" }
            tvColorDetail.text = intent.getStringExtra("color").orEmpty().ifEmpty { "-" }
            tvCapacityDetail.text = intent.getStringExtra("capacity").orEmpty().ifEmpty { "-" }

            // Seller Info
            tvSellerDetail.text = intent.getStringExtra("seller_name").orEmpty()
            tvContactDetail.text =
                intent.getStringExtra("seller_contact").orEmpty().ifEmpty { "Tidak tersedia" }
        }

        val thumbUrl = intent.getStringExtra("car_image_url").orEmpty()
        setupImageSlider(thumbUrl)

        // --- FETCH DATA LENGKAP ---
        if (carDocumentId != null) {
            db.collection("cars").document(carDocumentId!!).get().addOnSuccessListener { document ->
                    val car = document.toObject(Car::class.java)
                    if (car != null) {
                        updateSliderWithFullData(car)

                        binding.apply {
                            // 1. Pajak dengan Warna
                            if (car.taxStatus == "Mati") {
                                tvTaxInfoDetail.text = "Mati (s/d ${car.taxDate})"
                                tvTaxInfoDetail.setTextColor(
                                    ContextCompat.getColor(
                                        this@CarDetailActivity, R.color.red
                                    )
                                )
                            } else {
                                tvTaxInfoDetail.text = "Hidup"
                                tvTaxInfoDetail.setTextColor(
                                    ContextCompat.getColor(
                                        this@CarDetailActivity, R.color.green
                                    )
                                )
                            }

                            // 2. PLAT NOMOR VISUAL (SEKARANG DI BAWAH)
                            if (car.plateNumber.isNotEmpty()) {
                                layoutPlateDisplay.visibility = View.VISIBLE
                                tvPlateDisplay.text = car.plateNumber
                                tvPlateTypeDisplay.text = car.plateType
                            } else {
                                layoutPlateDisplay.visibility = View.GONE
                            }
                        }
                    }
                }
        }

        setupComments()
        setupActionButtons(
            intent.getStringExtra("seller_contact").orEmpty(),
            carName,
            intent.getIntExtra("car_year", 0),
            carPrice
        )

        binding.btnCalculator.setOnClickListener {
            showLoanCalculatorDialog()
        }
    }

    private fun shareCarListing() {
        val carName = binding.tvCarNameDetail.text.toString()
        val carPrice = binding.tvCarPriceBadge.text.toString()
        val carLoc = binding.tvCarLocationDetail.text.toString()

        val shareText = """
            Cek mobil ini di OtoMatch! 🚗💨
            
            *$carName*
            Harga: $carPrice
            Lokasi: $carLoc
            
            Tertarik? Yuk lihat detailnya di aplikasi OtoMatch!
        """.trimIndent()

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Bagikan mobil via...")
        startActivity(shareIntent)
    }

    private fun showLoanCalculatorDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_kalkulator, null)
        val etDP = dialogView.findViewById<TextInputEditText>(R.id.etDownPayment)
        val actTenor = dialogView.findViewById<AutoCompleteTextView>(R.id.actTenor)
        val btnHitung = dialogView.findViewById<Button>(R.id.btnCalculate)
        val tvResult = dialogView.findViewById<TextView>(R.id.tvMonthlyResult)

        val tenorOptions = listOf("1 Tahun", "2 Tahun", "3 Tahun", "4 Tahun", "5 Tahun")
        actTenor.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, tenorOptions))

        etDP.addTextChangedListener(NumberTextWatcher(etDP))

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        btnHitung.setOnClickListener {
            val dpString = NumberTextWatcher.cleanDigits(etDP.text.toString())
            val dp = dpString.toLongOrNull() ?: 0L
            val tenorString = actTenor.text.toString()
            val years = tenorString.split(" ")[0].toIntOrNull() ?: 0

            if (dp <= 0 || years == 0) {
                Toast.makeText(this, "Mohon lengkapi data DP dan Tenor.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (dp >= carPriceLong) {
                Toast.makeText(this, "Nominal DP tidak valid.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pokokHutang = carPriceLong - dp
            val bungaPerTahun = 0.08
            val totalBunga = (pokokHutang * bungaPerTahun * years).toLong()
            val totalHutang = pokokHutang + totalBunga
            val cicilanPerBulan = totalHutang / (years * 12)

            tvResult.text =
                "Estimasi Cicilan:\n${NumberTextWatcher.formatToRupiah(cicilanPerBulan)} / bulan"
        }
        dialog.show()
    }

    private fun setupImageSlider(thumbnailUrl: String) {
        val initialList = ArrayList<Any>()
        if (thumbnailUrl.isNotEmpty()) {
            initialList.add(thumbnailUrl)
        }
        val adapter = ImageSliderAdapter(initialList)
        binding.vpDetailImages.adapter = adapter
        binding.tvImageCount.text = if (initialList.isNotEmpty()) "1/1" else "0/0"
    }

    private fun updateSliderWithFullData(car: Car) {
        val rawImages = if (car.imageUrls.isNotEmpty()) car.imageUrls else listOf(car.imageUrl)
        if (rawImages.isNotEmpty() && rawImages[0].isNotEmpty()) {
            val images = ArrayList<Any>(rawImages)
            val adapter = ImageSliderAdapter(images)
            binding.vpDetailImages.adapter = adapter
            binding.tvImageCount.text = "1/${images.size}"

            binding.vpDetailImages.registerOnPageChangeCallback(object :
                ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.tvImageCount.text = "${position + 1}/${images.size}"
                }
            })
        }
    }

    private fun setupActionButtons(contact: String, name: String, year: Int, price: String) {
        // Tombol Telepon (Icon di dalam Card)
        binding.btnContactSeller.setOnClickListener {
            if (contact.isNotEmpty()) startActivity(
                Intent(
                    Intent.ACTION_DIAL, Uri.parse("tel:$contact")
                )
            )
            else Toast.makeText(this, "Nomor kontak tidak tersedia.", Toast.LENGTH_SHORT).show()
        }
        // Tombol WA
        binding.btnWhatsapp.setOnClickListener {
            if (contact.isNotEmpty()) {
                var phone = contact
                if (phone.startsWith("0")) phone = "62" + phone.substring(1)
                val msg =
                    "Halo, saya tertarik dengan mobil *$name ($year)* yang dijual seharga *$price* di OtoMatch. Apakah unit masih tersedia?"
                val url = "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(msg)}"
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Aplikasi WhatsApp tidak ditemukan.", Toast.LENGTH_SHORT)
                        .show()
                }
            }
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
                Toast.makeText(this, "Silakan login untuk mengirim komentar.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            if (text.isEmpty()) {
                binding.etCommentInput.error = "Komentar tidak boleh kosong"
                return@setOnClickListener
            }
            if (rating == 0f) {
                Toast.makeText(this, "Mohon beri rating bintang", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            db.collection("users").document(auth.currentUser!!.uid).get().addOnSuccessListener {
                val name = it.getString("name") ?: "Pengguna"
                val comment = Comment(name, text, rating, auth.currentUser!!.uid)
                db.collection("cars").document(carDocumentId!!).collection("comments").add(comment)
                    .addOnSuccessListener {
                        binding.etCommentInput.setText("")
                        binding.ratingBarInput.rating = 0f
                        commentList.add(0, comment)
                        commentAdapter.notifyItemInserted(0)
                        binding.rvComments.scrollToPosition(0)
                        updateAvgRating()
                        Toast.makeText(this, "Komentar terkirim.", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun loadComments(carId: String) {
        db.collection("cars").document(carId).collection("comments")
            .orderBy("rating", Query.Direction.DESCENDING).get().addOnSuccessListener { result ->
                commentList.clear()
                for (doc in result) commentList.add(doc.toObject(Comment::class.java))
                commentAdapter.notifyDataSetChanged()
                updateAvgRating()
            }
    }

    private fun updateAvgRating() {
        if (commentList.isNotEmpty()) {
            val avg = commentList.map { it.rating }.average()
            binding.tvAvgRating.text = "%.1f".format(avg)
        } else {
            binding.tvAvgRating.text = "0.0"
        }
    }
}