package com.example.c_otomatch

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        binding.toolbarCarDetail.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        carDocumentId = intent.getStringExtra("car_document_id")
        val carName = intent.getStringExtra("car_name").orEmpty()
        val carPrice = intent.getStringExtra("car_price").orEmpty()

        carPriceLong = try {
            NumberTextWatcher.cleanDigits(carPrice).toLong()
        } catch (e: Exception) { 0L }

        binding.apply {
            tvCarNameDetail.text = carName
            tvCarBrandDetail.text = intent.getStringExtra("car_brand").orEmpty()
            tvCarYearDetail.text = "Tahun: ${intent.getIntExtra("car_year", 0)}"
            tvCarPriceBadge.text = carPrice
            tvCarLocationDetail.text = "Lokasi: ${intent.getStringExtra("car_location").orEmpty()}"
            tvSellerDetail.text = "Penjual: ${intent.getStringExtra("seller_name").orEmpty()}"

            // --- FIX: Gunakan orEmpty() sebelum ifEmpty ---
            tvContactDetail.text = intent.getStringExtra("seller_contact").orEmpty().ifEmpty { "Tidak tersedia" }

            tvVariantDetail.text = "Varian: ${intent.getStringExtra("variant").orEmpty().ifEmpty { "-" }}"
            tvBodyDetail.text = "Tipe: ${intent.getStringExtra("body_type").orEmpty().ifEmpty { "-" }}"
            tvColorDetail.text = "Warna: ${intent.getStringExtra("color").orEmpty().ifEmpty { "-" }}"
            tvTransmissionDetail.text = "Transmisi: ${intent.getStringExtra("transmission").orEmpty().ifEmpty { "-" }}"
            tvFuelDetail.text = "Bahan Bakar: ${intent.getStringExtra("fuel").orEmpty().ifEmpty { "-" }}"
            tvKmRangeDetail.text = "Jarak Tempuh: ${intent.getStringExtra("car_mileage").orEmpty().ifEmpty { "-" }}"
            tvCapacityDetail.text = "Kapasitas Mesin: ${intent.getStringExtra("capacity").orEmpty().ifEmpty { "-" }}"
        }

        val thumbUrl = intent.getStringExtra("car_image_url").orEmpty()
        setupImageSlider(thumbUrl)
        if (carDocumentId != null) {
            db.collection("cars").document(carDocumentId!!).get()
                .addOnSuccessListener { document ->
                    val car = document.toObject(Car::class.java)
                    if (car != null) updateSliderWithFullData(car)
                }
        }

        setupComments()
        setupActionButtons(intent.getStringExtra("seller_contact").orEmpty(), carName, intent.getIntExtra("car_year", 0), carPrice)

        binding.btnCalculator.setOnClickListener {
            showLoanCalculatorDialog()
        }
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

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

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

            tvResult.text = "Estimasi Cicilan:\n${NumberTextWatcher.formatToRupiah(cicilanPerBulan)} / bulan"
        }

        dialog.show()
    }

    private fun setupImageSlider(thumbnailUrl: String) {
        val initialList = if (thumbnailUrl.isNotEmpty()) listOf(thumbnailUrl) else emptyList()
        val adapter = ImageSliderAdapter(initialList)
        binding.vpDetailImages.adapter = adapter
        binding.tvImageCount.text = if (initialList.isNotEmpty()) "1/1" else "0/0"
    }

    private fun updateSliderWithFullData(car: Car) {
        val images = if (car.imageUrls.isNotEmpty()) car.imageUrls else listOf(car.imageUrl)
        if (images.isNotEmpty() && images[0].isNotEmpty()) {
            val adapter = ImageSliderAdapter(images)
            binding.vpDetailImages.adapter = adapter
            binding.tvImageCount.text = "1/${images.size}"
            binding.vpDetailImages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.tvImageCount.text = "${position + 1}/${images.size}"
                }
            })
        }
    }

    private fun setupActionButtons(contact: String, name: String, year: Int, price: String) {
        binding.btnContactSeller.setOnClickListener {
            if (contact.isNotEmpty()) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contact")))
            else Toast.makeText(this, "Nomor kontak tidak tersedia.", Toast.LENGTH_SHORT).show()
        }
        binding.btnWhatsapp.setOnClickListener {
            if (contact.isNotEmpty()) {
                var phone = contact
                if (phone.startsWith("0")) phone = "62" + phone.substring(1)
                val msg = "Halo, saya tertarik dengan mobil *$name ($year)* yang dijual seharga *$price* di OtoMatch. Apakah unit masih tersedia?"
                val url = "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(msg)}"
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                catch (e: Exception) { Toast.makeText(this, "Aplikasi WhatsApp tidak ditemukan.", Toast.LENGTH_SHORT).show() }
            }
        }
        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Lihat mobil $name seharga $price ini di OtoMatch!")
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan lewat"))
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
                Toast.makeText(this, "Silakan login untuk mengirim komentar.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (text.isEmpty()) {
                binding.etCommentInput.error = "Komentar tidak boleh kosong"
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
                        Toast.makeText(this, "Komentar terkirim.", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun loadComments(carId: String) {
        db.collection("cars").document(carId).collection("comments")
            .orderBy("rating", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { result ->
                commentList.clear()
                for (doc in result) commentList.add(doc.toObject(Comment::class.java))
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