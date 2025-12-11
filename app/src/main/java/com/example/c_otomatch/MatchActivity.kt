package com.example.c_otomatch

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.c_otomatch.adapters.CarAdapter
import com.example.c_otomatch.adapters.MatchAdapter
import com.example.c_otomatch.databinding.ActivityMatchBinding
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.utils.NumberTextWatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yuyakaido.android.cardstackview.*

class MatchActivity : AppCompatActivity(), CardStackListener {

    private lateinit var binding: ActivityMatchBinding
    private lateinit var manager: CardStackLayoutManager
    private lateinit var adapter: MatchAdapter
    private val carList = mutableListOf<Car>()

    private lateinit var reviewAdapter: CarAdapter
    private val reviewList = mutableListOf<Car>()

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Variabel filter
    private var currentBudget: String = "Tampilkan Semua"
    private var currentType: String = "Tampilkan Semua"
    private var currentTrans: String = "Tampilkan Semua"
    private var currentFuel: String = "Tampilkan Semua"
    private var currentColor: String = "Tampilkan Semua"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setSupportActionBar(binding.toolbarMatch)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbarMatch.setNavigationOnClickListener { finish() }

        setupCardStack()
        setupReviewList()

        // Ambil data filter awal dari Intent
        currentBudget = intent.getStringExtra("FILTER_BUDGET") ?: "Tampilkan Semua"
        currentType = intent.getStringExtra("FILTER_TYPE") ?: "Tampilkan Semua"
        currentTrans = intent.getStringExtra("FILTER_TRANS") ?: "Tampilkan Semua"
        currentFuel = intent.getStringExtra("FILTER_FUEL") ?: "Tampilkan Semua"
        currentColor = intent.getStringExtra("FILTER_COLOR") ?: "Tampilkan Semua"

        loadCars()
        setupButtons()
    }

    // --- FUNGSI BARU: MENAMPILKAN DIALOG FILTER DI SINI ---
    private fun showMatchmakerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_matchmaker, null)
        val actBudget = dialogView.findViewById<AutoCompleteTextView>(R.id.actBudget)
        val actType = dialogView.findViewById<AutoCompleteTextView>(R.id.actType)
        val actTrans = dialogView.findViewById<AutoCompleteTextView>(R.id.actTrans)
        val actFuel = dialogView.findViewById<AutoCompleteTextView>(R.id.actFuel)
        val actColor = dialogView.findViewById<AutoCompleteTextView>(R.id.actColor)
        val btnFind = dialogView.findViewById<Button>(R.id.btnFindMatch)

        val budgets = listOf("Tampilkan Semua", "Di bawah 200 Juta", "200 - 500 Juta", "Di atas 500 Juta")
        val types = listOf("Tampilkan Semua", "SUV", "MPV", "Sedan", "Hatchback", "Coupe", "Van", "Pickup")
        val transmissions = listOf("Tampilkan Semua", "Manual", "Automatic", "CVT")
        val fuels = listOf("Tampilkan Semua", "Bensin", "Diesel", "Listrik", "Hybrid")
        val colors = listOf("Tampilkan Semua", "Hitam", "Putih", "Silver", "Abu-abu", "Merah", "Biru", "Hijau", "Kuning", "Coklat", "Oranye", "Lainnya")

        // Set Adapters
        actBudget.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, budgets))
        actType.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, types))
        actTrans.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, transmissions))
        actFuel.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, fuels))
        actColor.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, colors))

        // Set Default Values (Opsional: Bisa di-set sesuai filter terakhir)
        actBudget.setText(currentBudget, false)
        actType.setText(currentType, false)
        actTrans.setText(currentTrans, false)
        actFuel.setText(currentFuel, false)
        actColor.setText(currentColor, false)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnFind.setOnClickListener {
            // 1. Update variabel global dengan input baru
            currentBudget = actBudget.text.toString()
            currentType = actType.text.toString()
            currentTrans = actTrans.text.toString()
            currentFuel = actFuel.text.toString()
            currentColor = actColor.text.toString()

            // 2. Reload mobil dengan filter baru
            loadCars()

            Toast.makeText(this, "Mencari dengan filter baru...", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
    // -------------------------------------------------------

    private fun setupCardStack() {
        manager = CardStackLayoutManager(this, this)
        manager.setStackFrom(StackFrom.None)
        manager.setVisibleCount(3)
        manager.setTranslationInterval(8.0f)
        manager.setScaleInterval(0.95f)
        manager.setSwipeThreshold(0.3f)
        manager.setMaxDegree(20.0f)
        manager.setDirections(Direction.HORIZONTAL)
        manager.setCanScrollHorizontal(true)
        manager.setCanScrollVertical(false)
        manager.setSwipeableMethod(SwipeableMethod.AutomaticAndManual)
        manager.setOverlayInterpolator(LinearInterpolator())

        adapter = MatchAdapter(carList)
        binding.cardStackView.layoutManager = manager
        binding.cardStackView.adapter = adapter
        binding.cardStackView.itemAnimator.apply {
            if (this is DefaultItemAnimator) {
                supportsChangeAnimations = false
            }
        }

        adapter.setOnItemClickListener { car ->
            val intent = Intent(this, CarDetailActivity::class.java).apply {
                putExtra("car_document_id", car.documentId)
                putExtra("car_name", car.name)
                putExtra("car_brand", car.brand)
                putExtra("car_year", car.year)
                putExtra("car_price", car.price)
                putExtra("car_mileage", car.mileage)
                putExtra("car_location", car.location)
                val img = if (car.imageUrls.isNotEmpty()) car.imageUrls[0] else car.imageUrl
                putExtra("car_image_url", img)
                putExtra("seller_name", car.sellerName)
                putExtra("seller_contact", car.sellerContact)
                putExtra("body_type", car.bodyType)
                putExtra("color", car.color)
                putExtra("exactColor", car.exactColor)
                putExtra("transmission", car.transmission)
                putExtra("fuel", car.fuel)
                putExtra("variant", car.variant)
                putExtra("capacity", car.capacity)
                putExtra("negatives", car.negatives)
                putExtra("mods", car.mods)
            }
            startActivity(intent)
        }
    }

    private fun setupReviewList() {
        reviewAdapter = CarAdapter(
            reviewList,
            onItemClicked = { car ->
                val intent = Intent(this, CarDetailActivity::class.java)
                intent.putExtra("car_document_id", car.documentId)
                intent.putExtra("car_name", car.name)
                intent.putExtra("car_price", car.price)
                intent.putExtra("car_brand", car.brand)
                intent.putExtra("car_year", car.year)
                intent.putExtra("car_mileage", car.mileage)
                intent.putExtra("car_location", car.location)
                val img = if (car.imageUrls.isNotEmpty()) car.imageUrls[0] else car.imageUrl
                intent.putExtra("car_image_url", img)
                intent.putExtra("seller_name", car.sellerName)
                intent.putExtra("seller_contact", car.sellerContact)
                intent.putExtra("body_type", car.bodyType)
                intent.putExtra("color", car.color)
                intent.putExtra("exactColor", car.exactColor)
                intent.putExtra("transmission", car.transmission)
                intent.putExtra("fuel", car.fuel)
                intent.putExtra("variant", car.variant)
                intent.putExtra("capacity", car.capacity)
                intent.putExtra("negatives", car.negatives)
                intent.putExtra("mods", car.mods)
                startActivity(intent)
            },
            onMarkSoldClicked = {},
            isSellFragment = false
        )
        binding.rvMatchReview.layoutManager = LinearLayoutManager(this)
        binding.rvMatchReview.adapter = reviewAdapter
    }

    private fun loadCars() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.cardStackView.visibility = View.VISIBLE
        binding.buttonContainer.visibility = View.VISIBLE

        val user = auth.currentUser ?: return

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val wishlistIds = userDoc.get("wishlist") as? List<String> ?: emptyList()
                fetchCarsAndFilter(wishlistIds)
            }
            .addOnFailureListener {
                fetchCarsAndFilter(emptyList())
            }
    }

    private fun fetchCarsAndFilter(wishlistIds: List<String>) {
        db.collection("cars")
            .whereEqualTo("isSold", false)
            .get()
            .addOnSuccessListener { result ->
                carList.clear()
                val uid = auth.currentUser?.uid

                for (doc in result) {
                    val car = doc.toObject(Car::class.java)
                    car.documentId = doc.id

                    if (uid != null && car.sellerUid == uid) continue
                    if (wishlistIds.contains(car.documentId)) continue

                    val price = safePriceToLong(car.price)

                    val matchBudget = when(currentBudget) {
                        "Di bawah 200 Juta" -> price < 200_000_000
                        "200 - 500 Juta" -> price in 200_000_000..500_000_000
                        "Di atas 500 Juta" -> price > 500_000_000
                        else -> true
                    }

                    val matchType = isFilterMatch(currentType, car.bodyType)
                    val matchTrans = isFilterMatch(currentTrans, car.transmission)
                    val matchFuel = isFilterMatch(currentFuel, car.fuel)
                    val matchColor = isFilterMatch(currentColor, car.color)

                    if (matchBudget && matchType && matchTrans && matchFuel && matchColor) {
                        carList.add(car)
                    }
                }

                carList.shuffle()
                adapter.notifyDataSetChanged()

                if (carList.isEmpty()) {
                    showEmptyState()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun isFilterMatch(filterValue: String, carValue: String): Boolean {
        return if (filterValue == "Tampilkan Semua" || filterValue.isEmpty()) {
            true
        } else {
            carValue.equals(filterValue, ignoreCase = true)
        }
    }

    private fun showEmptyState() {
        binding.cardStackView.visibility = View.GONE
        binding.buttonContainer.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE

        loadWishlistReview()
    }

    private fun loadWishlistReview() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val wishlistIds = document.get("wishlist") as? List<String>
                    if (!wishlistIds.isNullOrEmpty()) {
                        db.collection("cars")
                            .whereIn(FieldPath.documentId(), wishlistIds)
                            .get()
                            .addOnSuccessListener { result ->
                                reviewList.clear()
                                for (doc in result) {
                                    val car = doc.toObject(Car::class.java)
                                    car.documentId = doc.id
                                    car.isWishlist = true
                                    reviewList.add(car)
                                }
                                reviewAdapter.updateList(reviewList)
                                reviewAdapter.updateWishlist(wishlistIds)
                            }
                    } else {
                        reviewList.clear()
                        reviewAdapter.notifyDataSetChanged()
                    }
                }
            }
    }

    private fun setupButtons() {
        binding.btnSkip.setOnClickListener {
            val setting = SwipeAnimationSetting.Builder()
                .setDirection(Direction.Left)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .build()
            manager.setSwipeAnimationSetting(setting)
            binding.cardStackView.swipe()
        }

        binding.btnLike.setOnClickListener {
            val setting = SwipeAnimationSetting.Builder()
                .setDirection(Direction.Right)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .build()
            manager.setSwipeAnimationSetting(setting)
            binding.cardStackView.swipe()
        }

        binding.btnUndo.setOnClickListener {
            val setting = RewindAnimationSetting.Builder()
                .setDirection(Direction.Bottom)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .build()
            manager.setRewindAnimationSetting(setting)
            binding.cardStackView.rewind()
        }

        // --- TOMBOL NAVIGASI BAWAH ---

        binding.btnNavHome.setOnClickListener {
            finish()
        }

        binding.btnNavReset.setOnClickListener {
            val options = arrayOf("Gunakan Filter Sama", "Atur Spesifikasi Baru")
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Ulangi Pencarian?")
            builder.setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Opsi: Filter Sama
                        loadCars()
                        Toast.makeText(this, "Memuat ulang...", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        // Opsi: Filter Baru -> Munculkan Dialog
                        showMatchmakerDialog()
                    }
                }
            }
            builder.show()
        }

        binding.btnNavWishlist.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            intent.putExtra("NAVIGATE_TO", "wishlist")
            startActivity(intent)
            finish()
        }
    }

    private fun safePriceToLong(priceStr: String?): Long {
        val safeString = priceStr ?: ""
        if (safeString.isBlank()) return 0L
        return try {
            NumberTextWatcher.cleanDigits(safeString).toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L }
    }

    override fun onCardDragging(direction: Direction?, ratio: Float) {
        val currentView = manager.findViewByPosition(manager.topPosition) ?: return
        val leftOverlay = currentView.findViewById<View>(R.id.left_overlay)
        val rightOverlay = currentView.findViewById<View>(R.id.right_overlay)

        leftOverlay.visibility = View.GONE
        rightOverlay.visibility = View.GONE

        if (direction == Direction.Right) {
            rightOverlay.visibility = View.VISIBLE
            rightOverlay.alpha = ratio
        } else if (direction == Direction.Left) {
            leftOverlay.visibility = View.VISIBLE
            leftOverlay.alpha = ratio
        }
    }

    override fun onCardSwiped(direction: Direction?) {
        val swipedIndex = manager.topPosition - 1
        if (swipedIndex in carList.indices) {
            val car = carList[swipedIndex]
            if (direction == Direction.Right) {
                addToWishlist(car)
            }
        }
        if (manager.topPosition == adapter.itemCount) {
            showEmptyState()
        }
    }

    private fun addToWishlist(car: Car) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid)
            .update("wishlist", FieldValue.arrayUnion(car.documentId))
            .addOnSuccessListener {
                Toast.makeText(this, "Disimpan ke Wishlist! ❤️", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal menyimpan wishlist", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onCardRewound() {
        Toast.makeText(this, "Undo berhasil ↺", Toast.LENGTH_SHORT).show()
    }
    override fun onCardCanceled() {}
    override fun onCardAppeared(view: View?, position: Int) {}
    override fun onCardDisappeared(view: View?, position: Int) {}
}