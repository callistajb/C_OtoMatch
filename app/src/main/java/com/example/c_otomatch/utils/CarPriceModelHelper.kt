package com.example.c_otomatch.utils

import android.content.Context
import android.content.res.AssetFileDescriptor
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.ln1p

class CarPriceModelHelper(context: Context) {

    private var interpreter: Interpreter? = null

    private val inputMean = floatArrayOf(24.5f, 2014.2f, 65000.0f, 1800.0f)
    private val inputStd = floatArrayOf(15.2f, 5.5f, 40000.0f, 600.0f)

    private val brandMap = mapOf(
        "Toyota" to 0f, "Honda" to 1f, "Daihatsu" to 2f, "Suzuki" to 3f, "Mitsubishi" to 4f,
        "Nissan" to 5f, "Mazda" to 6f, "Wuling" to 7f, "Hyundai" to 8f, "KIA" to 9f,
        "BMW" to 10f, "Mercedes-Benz" to 11f, "Lexus" to 12f, "Audi" to 13f, "Chevrolet" to 14f,
        "Ford" to 15f, "Isuzu" to 16f, "Datsun" to 17f, "Volkswagen" to 18f, "Peugeot" to 19f,
        "Renault" to 20f, "Jeep" to 21f, "Land Rover" to 22f, "Mini" to 23f, "Subaru" to 24f,
        "Volvo" to 25f, "Fiat" to 26f, "Porsche" to 27f, "Ferrari" to 28f, "Lamborghini" to 29f,
        "Aston Martin" to 30f, "McLaren" to 31f, "Bentley" to 32f, "Rolls-Royce" to 33f,
        "Maserati" to 34f, "Jaguar" to 35f, "Alfa Romeo" to 36f, "Tesla" to 37f, "Chery" to 38f,
        "DFSK" to 39f, "MG" to 40f, "BYD" to 41f, "Geely" to 42f, "Proton" to 43f,
        "Tata" to 44f, "Mahindra" to 45f, "Hino" to 46f, "Foton" to 47f, "Hummer" to 48f,
        "Cadillac" to 49f, "Lincoln" to 50f, "Chrysler" to 51f, "Dodge" to 52f, "GMC" to 53f,
        "Infiniti" to 54f, "Acura" to 55f, "Genesis" to 56f, "SsangYong" to 57f, "Timor" to 58f,
        "Bimantara" to 59f, "Esemka" to 60f, "Opel" to 61f, "Daewoo" to 62f, "Holden" to 63f,
        "Brabus" to 64f, "Alpina" to 65f, "Lotus" to 66f, "Smart" to 67f, "Citroen" to 68f,
        "Other" to 69f
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

        val brandCode = brandMap[brand] ?: brandMap["Other"]!!

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


        val luxuryMultiplier = when(brand) {
            "Ferrari", "Lamborghini", "Rolls-Royce", "Bentley", "McLaren", "Aston Martin" -> 8.0f // Super Luxury
            "Porsche", "Maserati", "Jaguar", "Land Rover", "Lexus", "Tesla", "Brabus" -> 3.5f // Luxury Sport
            "Mercedes-Benz", "BMW", "Audi", "Volvo", "Jeep", "Mini", "Subaru" -> 1.8f // Premium
            "Toyota", "Honda", "Mazda", "Mitsubishi", "Nissan", "Volkswagen" -> 1.1f // Middle-Up
            else -> 1.0f
        }

        val ccMultiplier = if (capacity > 2500) 1.5f else if (capacity > 1800) 1.2f else 1.0f

        return result * luxuryMultiplier * ccMultiplier
    }

    fun close() {
        interpreter?.close()
    }
}