package com.example.c_otomatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.bumptech.glide.Glide

class CarDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCarDetailBinding
    private lateinit var commentAdapter: CommentAdapter
    private val commentList = mutableListOf<Comment>()
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var carDocumentId: String? = null
    private var sellerUid: String? = null
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

        binding.btnShareHeader.setOnClickListener { shareCarListing() }

        carDocumentId = intent.getStringExtra("car_document_id")
        sellerUid = intent.getStringExtra("seller_uid")

        val carName = intent.getStringExtra("car_name").orEmpty()
        val carPrice = intent.getStringExtra("car_price").orEmpty()

        carPriceLong = try {
            NumberTextWatcher.cleanDigits(carPrice).toLong()
        } catch (e: Exception) {
            0L
        }

        setupStaticData(carName, carPrice)

        val thumbUrl = intent.getStringExtra("car_image_url").orEmpty()
        setupImageSlider(thumbUrl)

        if (carDocumentId != null) {
            loadFullCarData()
        }

        setupComments()

        setupActionButtons(
            intent.getStringExtra("seller_contact").orEmpty(),
            carName,
            intent.getIntExtra("car_year", 0),
            carPrice
        )

        binding.btnCalculator.setOnClickListener { showLoanCalculatorDialog() }
    }

    private fun setupComments() {
        val currentUserId = auth.currentUser?.uid

        commentAdapter = CommentAdapter(commentList, currentUserId) { commentToDelete ->
            showDeleteConfirmDialog(commentToDelete)
        }

        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(this@CarDetailActivity)
            adapter = commentAdapter
            setHasFixedSize(true)
        }
        if (carDocumentId != null) loadComments(carDocumentId!!)

        binding.btnSubmitComment.setOnClickListener {
            submitCommentLogic()
        }
    }

    private fun showDeleteConfirmDialog(comment: Comment) {
        AlertDialog.Builder(this).setTitle("Hapus Ulasan")
            .setMessage("Apakah Anda yakin ingin menghapus ulasan ini?")
            .setPositiveButton("Hapus") { _, _ ->
                deleteCommentFromFirestore(comment)
            }.setNegativeButton("Batal", null).show()
    }

    private fun deleteCommentFromFirestore(comment: Comment) {
        if (carDocumentId == null || comment.id.isEmpty()) return

        db.collection("cars").document(carDocumentId!!).collection("comments").document(comment.id)
            .delete().addOnSuccessListener {
                Toast.makeText(this, "Ulasan dihapus", Toast.LENGTH_SHORT).show()

                commentAdapter.removeItem(comment)

                recalculateCarRating()
            }.addOnFailureListener {
                Toast.makeText(this, "Gagal menghapus ulasan", Toast.LENGTH_SHORT).show()
            }
    }

    private fun submitCommentLogic() {
        val text = binding.etCommentInput.text.toString().trim()
        val rating = binding.ratingBarInput.rating

        if (auth.currentUser == null) {
            Toast.makeText(this, "Silakan login untuk mengirim ulasan.", Toast.LENGTH_SHORT).show()
            return
        }
        if (text.isEmpty()) {
            binding.etCommentInput.error = "Ulasan tidak boleh kosong"
            return
        }
        if (rating == 0f) {
            Toast.makeText(this, "Mohon beri bintang", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSubmitComment.isEnabled = false

        db.collection("users").document(auth.currentUser!!.uid).get().addOnSuccessListener {
            val name = it.getString("name") ?: "Pengguna"
            val photoUrl = it.getString("profileImageUrl") ?: ""

            val newCommentMap = hashMapOf(
                "userName" to name,
                "text" to text,
                "rating" to rating,
                "userId" to auth.currentUser!!.uid,
                "userPhotoUrl" to photoUrl,
                "timestamp" to FieldValue.serverTimestamp()
            )

            db.collection("cars").document(carDocumentId!!).collection("comments")
                .add(newCommentMap).addOnSuccessListener { docRef ->
                    binding.etCommentInput.setText("")
                    binding.ratingBarInput.rating = 0f

                    val localComment = Comment(
                        id = docRef.id,
                        userName = name,
                        text = text,
                        rating = rating,
                        userId = auth.currentUser!!.uid,
                        userPhotoUrl = photoUrl
                    )

                    commentAdapter.addComment(localComment)
                    binding.rvComments.scrollToPosition(0)

                    recalculateCarRating()

                    Toast.makeText(this, "Ulasan terkirim!", Toast.LENGTH_SHORT).show()
                    binding.btnSubmitComment.isEnabled = true
                }.addOnFailureListener {
                    binding.btnSubmitComment.isEnabled = true
                    Toast.makeText(this, "Gagal mengirim ulasan", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // --- RECALCULATE RATING ---
    private fun recalculateCarRating() {
        db.collection("cars").document(carDocumentId!!).collection("comments").get()
            .addOnSuccessListener { querySnapshot ->
                var total = 0.0
                var count = 0
                for (doc in querySnapshot) {
                    val r = doc.getDouble("rating") ?: 0.0
                    if (r > 0) {
                        total += r
                        count++
                    }
                }

                val avgCarRating = if (count > 0) total / count else 0.0

                db.collection("cars").document(carDocumentId!!).update("rating", avgCarRating)
                    .addOnSuccessListener {
                        binding.tvAvgRating.text = String.format("%.1f", avgCarRating)
                        if (!sellerUid.isNullOrEmpty()) {
                            recalculateSellerGlobalRating(sellerUid!!)
                        }
                    }
            }
    }

    private fun recalculateSellerGlobalRating(uid: String) {
        db.collection("cars").whereEqualTo("sellerUid", uid).get().addOnSuccessListener { result ->
            var totalRating = 0.0
            var carCount = 0

            for (doc in result) {
                val r = doc.getDouble("rating") ?: 0.0
                if (r > 0.0) {
                    totalRating += r
                    carCount++
                }
            }

            val finalSellerRating = if (carCount > 0) totalRating / carCount else 0.0

            db.collection("users").document(uid).update("rating", finalSellerRating)
                .addOnSuccessListener {
                    loadSellerRating(uid)
                }
        }
    }

    private fun loadComments(carId: String) {
        db.collection("cars").document(carId).collection("comments").orderBy(
            "timestamp", Query.Direction.DESCENDING
        ).get().addOnSuccessListener { result ->
            commentList.clear()
            for (doc in result) {
                val c = doc.toObject(Comment::class.java)
                c.id = doc.id
                commentList.add(c)
            }
            commentAdapter.notifyDataSetChanged()

            if (commentList.isNotEmpty()) {
                val avg = commentList.map { it.rating }.average()
                binding.tvAvgRating.text = "%.1f".format(avg)
            } else {
                binding.tvAvgRating.text = "0.0"
            }
        }
    }

    private fun setupStaticData(carName: String, carPrice: String) {
        binding.apply {
            tvCarNameDetail.text = carName
            tvCarBrandDetail.text = intent.getStringExtra("car_brand").orEmpty()
            tvCarYearDetail.text = intent.getIntExtra("car_year", 0).toString()
            tvKmRangeDetail.text = intent.getStringExtra("car_mileage").orEmpty()
            tvTransmissionDetail.text =
                intent.getStringExtra("transmission").orEmpty().ifEmpty { "-" }
            tvFuelDetail.text = intent.getStringExtra("fuel").orEmpty().ifEmpty { "-" }
            tvCarPriceBadge.text = carPrice
            tvCarLocationDetail.text = intent.getStringExtra("car_location").orEmpty()
            tvVariantDetail.text = intent.getStringExtra("variant").orEmpty().ifEmpty { "-" }
            tvBodyDetail.text = intent.getStringExtra("body_type").orEmpty().ifEmpty { "-" }
            tvColorDetail.text = intent.getStringExtra("color").orEmpty().ifEmpty { "-" }
            tvCapacityDetail.text = intent.getStringExtra("capacity").orEmpty().ifEmpty { "-" }
            tvSellerDetail.text = intent.getStringExtra("seller_name").orEmpty()
            tvContactDetail.text =
                intent.getStringExtra("seller_contact").orEmpty().ifEmpty { "Tidak tersedia" }
        }
    }

    private fun loadFullCarData() {
        db.collection("cars").document(carDocumentId!!).get().addOnSuccessListener { document ->
            val car = document.toObject(Car::class.java)
            if (car != null) {
                if (sellerUid.isNullOrEmpty()) sellerUid = car.sellerUid
                loadSellerRating(car.sellerUid)
                updateSliderWithFullData(car)
                binding.apply {
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

    private fun loadSellerRating(uid: String) {
        if (uid.isEmpty()) return
        db.collection("users").document(uid).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val rating = document.getDouble("rating")?.toFloat() ?: 0.0f
                val currentName = binding.tvSellerDetail.text.toString().split("★")[0].trim()
                binding.tvSellerDetail.text = "$currentName ★ ${String.format("%.1f", rating)}"

                val sellerPhotoUrl = document.getString("profileImageUrl")
                if (!sellerPhotoUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(sellerPhotoUrl)
                        .placeholder(R.drawable.ic_person)
                        .circleCrop()
                        .into(binding.ivSellerAvatar)
                    binding.ivSellerAvatar.clearColorFilter()
                    binding.ivSellerAvatar.setPadding(0, 0, 0, 0)
                }
            }
        }
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

    private fun setupImageSlider(thumbnailUrl: String) {
        val initialList = ArrayList<Any>()
        if (thumbnailUrl.isNotEmpty()) initialList.add(thumbnailUrl)
        val adapter = ImageSliderAdapter(initialList)
        binding.vpDetailImages.adapter = adapter
        binding.tvImageCount.text = if (initialList.isNotEmpty()) "1/1" else "0/0"
    }

    private fun setupActionButtons(contact: String, name: String, year: Int, price: String) {
        binding.btnContactSeller.setOnClickListener {
            if (contact.isNotEmpty()) startActivity(
                Intent(
                    Intent.ACTION_DIAL, Uri.parse("tel:$contact")
                )
            )
            else Toast.makeText(this, "Nomor kontak tidak tersedia.", Toast.LENGTH_SHORT).show()
        }
        binding.btnWhatsapp.setOnClickListener {
            if (contact.isNotEmpty()) {
                var phone = contact
                if (phone.startsWith("0")) phone = "62" + phone.substring(1)
                val msg =
                    "Halo, saya tertarik dengan mobil *$name ($year)* yang dijual seharga *$price* di OtoMatch."
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

    private fun showLoanCalculatorDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_kalkulator, null)
        val etDP = dialogView.findViewById<TextInputEditText>(R.id.etDownPayment)
        val etInterest = dialogView.findViewById<TextInputEditText>(R.id.etInterestRate)
        val actTenor = dialogView.findViewById<AutoCompleteTextView>(R.id.actTenor)
        val btnHitung = dialogView.findViewById<Button>(R.id.btnCalculate)
        val cvResult = dialogView.findViewById<View>(R.id.cvResultContainer)
        val tvPrincipal = dialogView.findViewById<TextView>(R.id.tvPrincipal)
        val tvTotalInterest = dialogView.findViewById<TextView>(R.id.tvTotalInterest)
        val tvMonthlyResult = dialogView.findViewById<TextView>(R.id.tvMonthlyResult)

        val tenorOptions = listOf("1 Tahun", "2 Tahun", "3 Tahun", "4 Tahun", "5 Tahun")
        actTenor.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, tenorOptions))

        val defaultDP = (carPriceLong * 0.2).toLong()
        etDP.setText(NumberTextWatcher.formatToRupiah(defaultDP))
        etDP.addTextChangedListener(NumberTextWatcher(etDP))

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnHitung.setOnClickListener {
            val dpString = NumberTextWatcher.cleanDigits(etDP.text.toString())
            val dp = dpString.toLongOrNull() ?: 0L
            val interestRateStr = etInterest.text.toString()
            val interestRate = interestRateStr.toDoubleOrNull() ?: 8.0
            val tenorString = actTenor.text.toString()
            val years = tenorString.split(" ")[0].toIntOrNull() ?: 3

            if (dp <= 0 || dp >= carPriceLong) {
                etDP.error = "DP tidak valid"; return@setOnClickListener
            }

            val pokokHutang = carPriceLong - dp
            val totalBunga = (pokokHutang * (interestRate / 100) * years).toLong()
            val totalPinjaman = pokokHutang + totalBunga
            val cicilanPerBulan = totalPinjaman / (years * 12)

            cvResult.visibility = View.VISIBLE
            tvPrincipal.text = NumberTextWatcher.formatToRupiah(pokokHutang)
            tvTotalInterest.text = "+ ${NumberTextWatcher.formatToRupiah(totalBunga)}"
            tvMonthlyResult.text = "${NumberTextWatcher.formatToRupiah(cicilanPerBulan)} /bln"
            cvResult.alpha = 0f
            cvResult.animate().alpha(1f).setDuration(300).start()
        }
        dialog.show()
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
            
            Tertarik? Yuk lihat detailnya di OtoMatch!
        """.trimIndent()

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Bagikan mobil via...")
        startActivity(shareIntent)
    }
}