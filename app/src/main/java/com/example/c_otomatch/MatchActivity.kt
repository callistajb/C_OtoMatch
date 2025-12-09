package com.example.c_otomatch

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import com.example.c_otomatch.adapters.MatchAdapter
import com.example.c_otomatch.databinding.ActivityMatchBinding
import com.example.c_otomatch.models.Car
import com.example.c_otomatch.utils.NumberTextWatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yuyakaido.android.cardstackview.*

class MatchActivity : AppCompatActivity(), CardStackListener {

    private lateinit var binding: ActivityMatchBinding
    private lateinit var manager: CardStackLayoutManager
    private lateinit var adapter: MatchAdapter
    private val carList = mutableListOf<Car>()

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // 2. Perbaikan Tombol Back
        setSupportActionBar(binding.toolbarMatch)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbarMatch.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupCardStack()

        // Ambil filter dari HomeFragment
        val budgetFilter = intent.getStringExtra("FILTER_BUDGET") ?: "Tampilkan Semua"
        val typeFilter = intent.getStringExtra("FILTER_TYPE") ?: "Tampilkan Semua"

        loadCars(budgetFilter, typeFilter)
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
    }

    private fun loadCars(budgetFilter: String, typeFilter: String) {
        // Ambil semua mobil yang belum terjual
        db.collection("cars")
            .whereEqualTo("isSold", false)
            .get()
            .addOnSuccessListener { result ->
                carList.clear()
                val uid = auth.currentUser?.uid

                for (doc in result) {
                    val car = doc.toObject(Car::class.java)
                    car.documentId = doc.id

                    // Filter 1: Jangan tampilkan mobil sendiri
                    if (uid != null && car.sellerUid == uid) continue

                    // Filter 2: Filter Budget
                    val price = safePriceToLong(car.price)
                    val budgetMatch = when(budgetFilter) {
                        "Di bawah 200 Juta" -> price < 200_000_000
                        "200 - 500 Juta" -> price in 200_000_000..500_000_000
                        "Di atas 500 Juta" -> price > 500_000_000
                        else -> true
                    }

                    // Filter 3: Filter Tipe Body
                    val typeMatch = if (typeFilter == "Tampilkan Semua" || typeFilter.isEmpty()) true
                    else car.bodyType.equals(typeFilter, ignoreCase = true)

                    if (budgetMatch && typeMatch) {
                        carList.add(car)
                    }
                }

                carList.shuffle() // Acak urutan
                adapter.notifyDataSetChanged()

                if (carList.isEmpty()) {
                    Toast.makeText(this, "Tidak ada mobil yang cocok dengan filter!", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data", Toast.LENGTH_SHORT).show()
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
    }

    private fun safePriceToLong(priceStr: String?): Long {
        val safeString = priceStr ?: ""
        if (safeString.isBlank()) return 0L
        return try {
            NumberTextWatcher.cleanDigits(safeString).toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L }
    }

    // --- CardStackListener Implementations ---

    // 4. Perbaikan Visual Feedback (Overlay) saat Swipe
    override fun onCardDragging(direction: Direction?, ratio: Float) {
        val currentView = manager.findViewByPosition(manager.topPosition) ?: return

        // Cari view overlay di dalam item_match_card.xml
        val leftOverlay = currentView.findViewById<View>(R.id.left_overlay)
        val rightOverlay = currentView.findViewById<View>(R.id.right_overlay)

        if (direction == Direction.Right) {
            // Geser Kanan -> Tampilkan Overlay Hijau (Wishlist)
            rightOverlay.visibility = View.VISIBLE
            rightOverlay.alpha = ratio
            leftOverlay.visibility = View.GONE
        } else if (direction == Direction.Left) {
            // Geser Kiri -> Tampilkan Overlay Merah (Skip)
            leftOverlay.visibility = View.VISIBLE
            leftOverlay.alpha = ratio
            rightOverlay.visibility = View.GONE
        }
    }

    override fun onCardSwiped(direction: Direction?) {
        val position = manager.topPosition - 1
        if (position >= 0 && position < carList.size) {
            val car = carList[position]

            if (direction == Direction.Right) {
                addToWishlist(car)
            }
        }

        if (manager.topPosition == adapter.itemCount) {
            Toast.makeText(this, "Mobil sudah habis! Cek lagi nanti.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addToWishlist(car: Car) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid)
            .update("wishlist", FieldValue.arrayUnion(car.documentId))
            .addOnSuccessListener {
                Toast.makeText(this, "Disimpan ke Wishlist! ❤️", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onCardRewound() {}
    override fun onCardCanceled() {}
    override fun onCardAppeared(view: View?, position: Int) {}
    override fun onCardDisappeared(view: View?, position: Int) {}
}