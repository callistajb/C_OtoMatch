package com.example.c_otomatch.utils

import android.content.Context
import android.content.res.AssetFileDescriptor
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class CarPriceModelHelper(context: Context) {

    private var interpreter: Interpreter? = null

    // Statistik Data (Sesuaikan dengan Python)
    private val inputMean = floatArrayOf(9.0012f, 2010.3407f, 69833.2891f, 1759.5483f)
    private val inputStd = floatArrayOf(8.4220f, 6.8284f, 53955.9141f, 577.9158f)

    private val brandMap = mapOf(
        "Honda" to 0f, "Toyota" to 1f, "Daihatsu" to 2f, "Suzuki" to 3f, "Mazda" to 4f,
        "BMW" to 5f, "Nissan" to 6f, "Mercedes-Benz" to 7f, "Mitsubishi" to 8f,
        "Chevrolet" to 9f, "Isuzu" to 10f, "Hyundai" to 11f, "Ford" to 12f,
        "KIA" to 13f, "Datsun" to 14f, "Jeep" to 15f, "Volkswagen" to 16f,
        "Land Rover" to 17f, "Lexus" to 18f, "Mini Cooper" to 19f, "Peugeot" to 20f,
        "Wuling" to 21f, "Timor" to 22f, "Porsche" to 23f, "Proton" to 24f,
        "Hino" to 25f, "Audi" to 26f, "Opel" to 27f, "Volvo" to 28f, "Subaru" to 29f,
        "Chery" to 30f, "Klasik" to 31f, "Lainnya" to 32f, "Jaguar" to 33f,
        "Fiat" to 34f, "Hummer" to 35f, "Dodge" to 36f, "Tata" to 37f,
        "Holden" to 38f, "Smart" to 39f, "Lamborghini" to 40f, "Chrysler" to 41f,
        "Ferrari" to 42f, "Geely" to 43f, "Cadillac" to 44f, "DFSK" to 45f,
        "Renault" to 46f, "Bentley" to 47f, "Mobil CBU" to 48f, "Maserati" to 49f,
        "Other" to 50f
    )

    init {
        try {
            interpreter = Interpreter(loadModelFile(context, "car_price_model.tflite"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun predict(brand: String, year: Int, mileage: Int, capacity: Int): Float {
        if (interpreter == null) return 0f

        val brandCode = brandMap[brand] ?: 50f

        val normBrand = (brandCode - inputMean[0]) / inputStd[0]
        val normYear = (year.toFloat() - inputMean[1]) / inputStd[1]
        val normMileage = (mileage.toFloat() - inputMean[2]) / inputStd[2]
        val normCapacity = (capacity.toFloat() - inputMean[3]) / inputStd[3]

        val inputs = Array(1) { FloatArray(4) }
        inputs[0][0] = normBrand
        inputs[0][1] = normYear
        inputs[0][2] = normMileage
        inputs[0][3] = normCapacity

        val outputs = Array(1) { FloatArray(1) }
        interpreter?.run(inputs, outputs)

        var result = outputs[0][0]
        if (result < 0) result = 0f
        return result
    }

    fun close() {
        interpreter?.close()
    }
}