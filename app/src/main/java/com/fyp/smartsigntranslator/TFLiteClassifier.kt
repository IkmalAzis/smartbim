package com.fyp.smartsigntranslator

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteClassifier(context: Context) {
    private val labels = arrayOf(
        "A", "ABANG", "AIR", "AMBIL", "ANJING", "ARNAB", "AWAK", "AYAH", "B", "BACA",
        "BAGAIMANA", "BELAJAR", "BELI", "BERAPA", "BERI", "BILA", "BUKU", "C", "D", "DAN",
        "DENGAN", "DOBI", "E", "EMAK", "F", "FERI", "G", "H", "HAI", "HOSPITAL",
        "HOTEL", "I", "INI", "J", "K", "KAMU", "KATIL", "KE", "KEDAI", "KENAPA",
        "KERJA", "KITA", "KUCING", "L", "LEMBU", "LIHAT", "LUKIS", "M", "MAAF", "MAIN",
        "MAJALAH", "MAKAN", "MANA", "MANDI", "MILO", "MINUM", "MOTOSIKAL", "N", "NASI", "O",
        "P", "PEJABAT", "PERGI", "POTONG", "PUKUL", "Q", "R", "ROTI", "RUMAH", "S",
        "SAYA", "SEKOLAH", "SIAPA", "T", "TEH", "TELUR", "TERIMA KASIH", "TIDUR", "U", "V",
        "W", "X", "Y", "Z"
    )

    private var interpreter: Interpreter? = null

    init {
        try {
            interpreter = Interpreter(loadModelFile(context))
            val inputTensor = interpreter?.getInputTensor(0)
            Log.d("TFLite", "Input shape: ${inputTensor?.shape()?.contentToString()}")
            Log.d("TFLite", "Input size: ${inputTensor?.numElements()}")
            Log.d("TFLite", "Labels count: ${labels.size}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("model_dynamic.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun predict(input: FloatArray): Pair<String, Float> {
        if (interpreter == null) return Pair("Error", 0f)
        val out = Array(1) { FloatArray(labels.size) }
        return try {
            interpreter?.run(arrayOf(input), out)
            val idx = out[0].indices.maxByOrNull { out[0][it] } ?: 0
            Pair(labels[idx], out[0][idx])
        } catch (e: Exception) {
            e.printStackTrace()
            Pair("Predict Error", 0f)
        }
    }

    fun close() {
        interpreter?.close()
    }
}