package com.example.c_otomatch

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
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

    private var currentBudgetFilter: String = "Tampilkan Semua"
    private var currentTypeFilter: String = "Tampilkan Semua"

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
        setupReviewList() // Setup RecyclerView untuk review

        currentBudgetFilter = intent.getStringExtra("FILTER_BUDGET") ?: "Tampilkan Semua"
        currentTypeFilter = intent.getStringExtra("FILTER_TYPE") ?: "Tampilkan Semua"

        loadCars(currentBudgetFilter, currentTypeFilter)
        setupButtons()
    }

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

        // 3. KLIK KARTU -> BUKA DETAIL
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

    // SETUP RECYCLER VIEW UNTUK REVIEW
    private fun setupReviewList() {
        reviewAdapter = CarAdapter(
            reviewList,
            onItemClicked = { car ->
                // Bisa buka detail juga dari sini
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

    private fun loadCars(budgetFilter: String, typeFilter: String) {
        binding.emptyStateLayout.visibility = View.GONE
        binding.cardStackView.visibility = View.VISIBLE
        binding.buttonContainer.visibility = View.VISIBLE

        db.collection("cars")
            .whereEqualTo("isSold", false)
            .get()
            .addOnSuccessListener { result ->
                carList.clear()
                val uid = auth.currentUser?.uid

                for (doc in result) {
                    val car = doc.toObject(Car::class.java)
                    car.documentId = doc.id

                    // Filter: Jangan tampilkan mobil sendiri
                    if (uid != null && car.sellerUid == uid) continue

                    // LOGIKA FILTER
                    val price = safePriceToLong(car.price)
                    val budgetMatch = when(budgetFilter) {
                        "Di bawah 200 Juta" -> price < 200_000_000
                        "200 - 500 Juta" -> price in 200_000_000..500_000_000
                        "Di atas 500 Juta" -> price > 500_000_000
                        else -> true
                    }

                    val typeMatch = if (typeFilter == "Tampilkan Semua" || typeFilter.isEmpty()) true
                    else car.bodyType.equals(typeFilter, ignoreCase = true)

                    if (budgetMatch && typeMatch) {
                        carList.add(car)
                    }
                }

                carList.shuffle() // Acak agar variatif
                adapter.notifyDataSetChanged()

                // Jika kosong dari awal
                if (carList.isEmpty()) {
                    showEmptyState()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat: ${e.message}", Toast.LENGTH_SHORT).show()
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

        // 1. Ambil list ID dari User
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val wishlistIds = document.get("wishlist") as? List<String>

                    if (!wishlistIds.isNullOrEmpty()) {
                        // 2. Ambil detail mobil berdasarkan ID
                        db.collection("cars")
                            .whereIn(FieldPath.documentId(), wishlistIds)
                            .get()
                            .addOnSuccessListener { result ->
                                reviewList.clear()
                                for (doc in result) {
                                    val car = doc.toObject(Car::class.java)
                                    car.documentId = doc.id
                                    car.isWishlist = true // Agar icon love merah
                                    reviewList.add(car)
                                }
                                // Update Adapter Review
                                reviewAdapter.updateList(reviewList)
                                // Pass ID list ke adapter supaya icon love tetap menyala
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
        // Tombol SKIP
        binding.btnSkip.setOnClickListener {
            val setting = SwipeAnimationSetting.Builder()
                .setDirection(Direction.Left)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .build()
            manager.setSwipeAnimationSetting(setting)
            binding.cardStackView.swipe()
        }

        // Tombol LIKE
        binding.btnLike.setOnClickListener {
            val setting = SwipeAnimationSetting.Builder()
                .setDirection(Direction.Right)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .build()
            manager.setSwipeAnimationSetting(setting)
            binding.cardStackView.swipe()
        }

        // 4. FITUR UNDO (REWIND)
        binding.btnUndo.setOnClickListener {
            val setting = RewindAnimationSetting.Builder()
                .setDirection(Direction.Bottom)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .build()
            manager.setRewindAnimationSetting(setting)
            binding.cardStackView.rewind()
        }

        // 5. OPSI "MULAI LAGI" DENGAN FILTER YANG SAMA
        binding.btnReset.setOnClickListener {
            loadCars(currentBudgetFilter, currentTypeFilter)
        }

        // 6. OPSI "KEMBALI" KE HOME
        binding.btnBackHome.setOnClickListener {
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

    // --- CardStackListener Implementations ---

    // 7. VISUAL FEEDBACK SAAT DRAGGING
    override fun onCardDragging(direction: Direction?, ratio: Float) {
        val currentView = manager.findViewByPosition(manager.topPosition) ?: return

        val leftOverlay = currentView.findViewById<View>(R.id.left_overlay)
        val rightOverlay = currentView.findViewById<View>(R.id.right_overlay)

        // Reset visibility dulu
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

    // 8. LOGIC WISHLIST (FIXED)
    override fun onCardSwiped(direction: Direction?) {
        // Logika CardStackView: Saat onCardSwiped dipanggil, topPosition SUDAH bertambah.
        // Jadi kartu yang baru saja di-swipe ada di posisi (manager.topPosition - 1)
        val swipedIndex = manager.topPosition - 1

        if (swipedIndex in carList.indices) {
            val car = carList[swipedIndex]

            if (direction == Direction.Right) {
                addToWishlist(car)
            }
        }

        // Cek jika kartu sudah habis
        if (manager.topPosition == adapter.itemCount) {
            showEmptyState()
        }
    }

    private fun addToWishlist(car: Car) {
        val user = auth.currentUser ?: return
        // Gunakan arrayUnion agar tidak duplikat
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