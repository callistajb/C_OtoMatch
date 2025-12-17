package com.example.c_otomatch.utils

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import org.tensorflow.lite.task.vision.classifier.ImageClassifier.ImageClassifierOptions
import java.io.IOException

class ImageClassifierHelper(
    val context: Context,
    val onError: (String) -> Unit,
    // Callback: (IsCar, DetectedObjectName)
    val onResult: (Boolean, String) -> Unit
) {
    private var imageClassifier: ImageClassifier? = null

    init {
        setupImageClassifier()
    }

    private fun setupImageClassifier() {
        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(2)

        // Kita ambil 1 hasil teratas saja yang paling mirip
        val optionsBuilder = ImageClassifierOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMaxResults(1)
            .setScoreThreshold(0.4f) // Minimal yakin 40%

        try {
            // Pastikan nama file sama dengan di assets
            imageClassifier = ImageClassifier.createFromFileAndOptions(
                context,
                "mobilenet_v1_1.0_224_quant.tflite",
                optionsBuilder.build()
            )
        } catch (e: IOException) {
            onError("Gagal load model: ${e.message}")
        } catch (e: IllegalStateException) {
            onError("TFLite Error: ${e.message}")
        }
    }

    fun classify(bitmap: Bitmap) {
        if (imageClassifier == null) setupImageClassifier()

        if (imageClassifier == null) {
            onResult(true, "Model Error") // Loloskan jika error
            return
        }

        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results = imageClassifier?.classify(tensorImage)

        // Daftar kata kunci yang dianggap MOBIL
        val carKeywords = listOf(
            "sports car", "minivan", "convertible", "cab", "racer", "jeep",
            "limousine", "beach wagon", "station wagon", "pickup", "ambulance",
            "police van", "tow truck", "trailer truck", "fire engine", "motor scooter",
            "moped", "car", "vehicle", "taxi", "bus", "truck", "recreational vehicle"
        )

        var isCar = false
        var detectedObject = "Tidak dikenali"

        if (!results.isNullOrEmpty() && results[0].categories.isNotEmpty()) {
            val topCategory = results[0].categories[0]
            detectedObject = topCategory.label // Ambil nama labelnya saja (tanpa skor)

            // Cek apakah label mengandung kata kunci mobil
            if (carKeywords.any { detectedObject.contains(it, ignoreCase = true) }) {
                isCar = true
            }
        }

        onResult(isCar, detectedObject)
    }
}