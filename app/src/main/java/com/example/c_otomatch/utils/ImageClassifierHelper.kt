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
    val onResult: (Boolean, String) -> Unit
) {
    private var imageClassifier: ImageClassifier? = null

    init {
        setupImageClassifier()
    }

    private fun setupImageClassifier() {
        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(2)

        val optionsBuilder = ImageClassifierOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMaxResults(3)
            .setScoreThreshold(0.3f)

        try {
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
            onResult(true, "Model Error")
            return
        }

        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results = imageClassifier?.classify(tensorImage)

        val carKeywords = listOf(
            "sports car", "minivan", "convertible", "cab", "racer", "jeep",
            "limousine", "beach wagon", "station wagon", "pickup", "ambulance",
            "police van", "tow truck", "trailer truck", "fire engine", "motor scooter",
            "moped", "car", "vehicle", "taxi", "bus", "truck", "recreational vehicle",
            "landrover", "off-road", "suv", "mpv", "sedan", "hatchback", "coupe",
            "motorcycle", "van", "minibus", "trolleybus", "go-kart", "golfcart",

            "grille", "radiator", "wheel", "tire", "windshield", "bumper",
            "headlight", "steering wheel", "seat belt", "car mirror", "wing mirror",
            "license plate", "traffic light", "parking meter", "gas pump", "speedometer"
        )

        var isCar = false
        var detectedObject = "Tidak dikenali"

        if (!results.isNullOrEmpty() && results[0].categories.isNotEmpty()) {
            for (category in results[0].categories) {
                val label = category.label.lowercase()
                detectedObject = category.label

                if (carKeywords.any { label.contains(it, ignoreCase = true) }) {
                    isCar = true
                    break
                }
            }
        }

        onResult(isCar, detectedObject)
    }
}