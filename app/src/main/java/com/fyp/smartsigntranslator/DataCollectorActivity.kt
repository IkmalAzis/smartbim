package com.fyp.smartsigntranslator

import android.graphics.Color
import android.os.*
import android.util.Log
import android.util.Size
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

class DataCollectorActivity : AppCompatActivity() {
    private lateinit var handLandmarker: HandLandmarker
    private lateinit var handOverlay: HandOverlayView
    private lateinit var labelInput: EditText
    private var lastLandmarks: FloatArray? = null
    private var lastSecondHand: FloatArray? = null  // secondary hand coords
    private var startCoords: FloatArray? = null
    private var startSecondHand: FloatArray? = null  // secondary hand at start

    private var isStaticMode = true
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var lastProcessTime = 0L
    private val minFrameInterval = 33L
    private lateinit var cameraExecutor: ExecutorService
    private var isTimerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_collector)

        handOverlay = findViewById(R.id.handOverlay)
        val statusText = findViewById<TextView>(R.id.statusText)
        labelInput = findViewById(R.id.labelInput)
        val btnSwitch = findViewById<ImageButton>(R.id.btnSwitchCamera)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnEnd = findViewById<Button>(R.id.btnEnd)
        val btnModeStatic = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnModeStatic)
        val btnModeMotion = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnModeMotion)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupHandLandmarker()
        startCamera()

        btnModeStatic.setOnClickListener {
            isStaticMode = true
            btnModeStatic.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981"))
            btnModeStatic.setTextColor(Color.WHITE)
            btnModeMotion.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#333333"))
            btnModeMotion.setTextColor(Color.parseColor("#AAAAAA"))
            statusText.text = "Mode: STATIC — press START to snap"
            statusText.setTextColor(Color.WHITE)
        }

        btnModeMotion.setOnClickListener {
            isStaticMode = false
            btnModeMotion.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981"))
            btnModeMotion.setTextColor(Color.WHITE)
            btnModeStatic.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#333333"))
            btnModeStatic.setTextColor(Color.parseColor("#AAAAAA"))
            statusText.text = "Mode: MOTION — START → END"
            statusText.setTextColor(Color.WHITE)
        }

        btnSwitch?.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
                CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            startCamera()
        }

        btnStart.setOnClickListener {
            val label = labelInput.text.toString().trim().uppercase()
            if (label.isEmpty()) {
                statusText.text = "Error: Type label first!"
                statusText.setTextColor(Color.RED)
                return@setOnClickListener
            }
            if (isTimerRunning) return@setOnClickListener

            if (isStaticMode) {
                captureStatic(label, statusText, btnStart, btnEnd)
            } else {
                captureMotion(label, statusText, btnStart, btnEnd)
            }
        }

        btnEnd.setOnClickListener {
            val label = labelInput.text.toString().trim().uppercase()
            if (label.isEmpty()) {
                statusText.text = "Error: Type label first!"
                statusText.setTextColor(Color.RED)
                return@setOnClickListener
            }
            if (startCoords != null && lastLandmarks != null) {
                saveToCSV(startCoords!!, lastLandmarks!!, label, startSecondHand, lastSecondHand)
                statusText.text = "Data SAVED! Ready for next."
                statusText.setTextColor(Color.GREEN)
                updateCountDisplay(label)
                startCoords = null
            } else {
                statusText.text = "Error: Press START first!"
                statusText.setTextColor(Color.RED)
            }
        }

        labelInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val label = s.toString().trim().uppercase()
                if (label.isNotEmpty()) updateCountDisplay(label)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // STATIC MODE — snap start and end close together
    private fun captureStatic(label: String, statusText: TextView, btnStart: Button, btnEnd: Button) {
        if (lastLandmarks == null) {
            statusText.text = "Hand not detected!"
            statusText.setTextColor(Color.RED)
            return
        }
        isTimerRunning = true
        btnStart.isEnabled = false
        btnEnd.isEnabled = false
        statusText.text = "Capturing static..."
        statusText.setTextColor(Color.CYAN)

        val snap1 = lastLandmarks!!.copyOf()
        val snap1Second = lastSecondHand?.copyOf()
        Handler(Looper.getMainLooper()).postDelayed({
            val snap2 = lastLandmarks?.copyOf() ?: snap1
            val snap2Second = lastSecondHand?.copyOf()
            saveToCSV(snap1, snap2, label, snap1Second, snap2Second)
            statusText.text = "SAVED! [$label]"
            statusText.setTextColor(Color.GREEN)
            updateCountDisplay(label)
            isTimerRunning = false
            btnStart.isEnabled = true
            btnEnd.isEnabled = true
        }, 300L)
    }

    // MOTION MODE — countdown START then END
    private fun captureMotion(label: String, statusText: TextView, btnStart: Button, btnEnd: Button) {
        btnStart.isEnabled = false
        btnEnd.isEnabled = false
        isTimerRunning = true
        startCoords = null

        startCountdown(statusText, "Ready START position...", "Snap START in: ", 3) {
            if (lastLandmarks != null) {
                startCoords = lastLandmarks?.copyOf()
                startSecondHand = lastSecondHand?.copyOf()
                statusText.text = "START captured! Change to END position..."
                statusText.setTextColor(Color.CYAN)

                Handler(Looper.getMainLooper()).postDelayed({
                    startCountdown(statusText, "Ready END position...", "Snap END in: ", 3) {
                        if (startCoords != null && lastLandmarks != null) {
                            saveToCSV(startCoords!!, lastLandmarks!!, label, startSecondHand, lastSecondHand)
                            statusText.text = "SAVED! [$label]"
                            statusText.setTextColor(Color.GREEN)
                            updateCountDisplay(label)
                        } else {
                            statusText.text = "Hand not detected during END!"
                            statusText.setTextColor(Color.RED)
                        }
                        startCoords = null
                        isTimerRunning = false
                        btnStart.isEnabled = true
                        btnEnd.isEnabled = true
                    }
                }, 1000L)
            } else {
                statusText.text = "Hand not detected!"
                statusText.setTextColor(Color.RED)
                isTimerRunning = false
                btnStart.isEnabled = true
                btnEnd.isEnabled = true
            }
        }
    }

    private fun startCountdown(statusText: TextView, startMessage: String,
        countdownPrefix: String, seconds: Int, onFinish: () -> Unit) {
        statusText.text = startMessage
        statusText.setTextColor(Color.YELLOW)
        object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                statusText.text = "$countdownPrefix${(millisUntilFinished / 1000) + 1}"
                statusText.setTextColor(Color.YELLOW)
            }
            override fun onFinish() { onFinish() }
        }.start()
    }

    private fun computeCurlFeatures(coords: FloatArray): FloatArray {
        val fingerJoints = arrayOf(
            Triple(1, 2, 3),
            Triple(5, 6, 7),
            Triple(9, 10, 11),
            Triple(13, 14, 15),
            Triple(17, 18, 19)
        )
        return FloatArray(5) { i ->
            val (base, mid, tip) = fingerJoints[i]
            val bx = coords[base*3]; val by = coords[base*3+1]; val bz = coords[base*3+2]
            val mx = coords[mid*3];  val my = coords[mid*3+1];  val mz = coords[mid*3+2]
            val tx = coords[tip*3];  val ty = coords[tip*3+1];  val tz = coords[tip*3+2]
            val v1x = bx-mx; val v1y = by-my; val v1z = bz-mz
            val v2x = tx-mx; val v2y = ty-my; val v2z = tz-mz
            val dot = v1x*v2x + v1y*v2y + v1z*v2z
            val mag = Math.sqrt((v1x*v1x+v1y*v1y+v1z*v1z).toDouble()) *
                      Math.sqrt((v2x*v2x+v2y*v2y+v2z*v2z).toDouble()) + 1e-8
            Math.acos((dot/mag).coerceIn(-1.0, 1.0)).toFloat()
        }
    }

    private fun saveToCSV(start: FloatArray, end: FloatArray, label: String,
                          secondStart: FloatArray? = null, secondEnd: FloatArray? = null) {
        try {
            val file = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "my_dataset_v2.csv")
            val writer = FileWriter(file, true)
            val sb = StringBuilder()

            val startCurl = computeCurlFeatures(start)
            val endCurl = computeCurlFeatures(end)
            val hand2Start = secondStart ?: FloatArray(63)
            val hand2End = secondEnd ?: FloatArray(63)

            // 262 features: [Hand2Start63] + [Hand1Start63] + [StartCurl5]
            //             + [Hand2End63] + [Hand1End63] + [EndCurl5]
            hand2Start.forEach { sb.append("$it,") }
            start.forEach { sb.append("$it,") }
            startCurl.forEach { sb.append("$it,") }
            hand2End.forEach { sb.append("$it,") }
            end.forEach { sb.append("$it,") }
            endCurl.forEach { sb.append("$it,") }

            writer.append(sb.append("$label\n").toString())
            writer.close()
            Log.d("CSV", "Saved: $label (dual-hand)")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun countSamplesForLabel(label: String): Int {
        return try {
            val file = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "my_dataset_v2.csv")
            if (!file.exists()) return 0
            file.readLines().count { line ->
                val trimmed = line.trimEnd()
                trimmed.endsWith(",$label") || trimmed.endsWith(",$label\r")
            }
        } catch (e: Exception) { 0 }
    }

    private fun updateCountDisplay(label: String) {
        val tvCount = findViewById<TextView>(R.id.tvSampleCount)
        val count = countSamplesForLabel(label)
        tvCount.text = "[$label] Samples: $count / 30"
        tvCount.setTextColor(when {
            count >= 30 -> Color.GREEN
            count >= 20 -> Color.YELLOW
            else -> Color.WHITE
        })
    }

    private fun extractHandCoords(
        lm: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>
    ): FloatArray {
        val wrist = lm[0]
        val flat = FloatArray(63)
        var i = 0
        for (point in lm) {
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                flat[i++] = point.x() - wrist.x()
                flat[i++] = point.y() - wrist.y()
            } else {
                flat[i++] = wrist.x() - point.x()
                flat[i++] = wrist.y() - point.y()
            }
            flat[i++] = point.z() - wrist.z()
        }
        return flat
    }

    private fun setupHandLandmarker() {
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .setDelegate(Delegate.GPU).build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.6f)
            .setMinHandPresenceConfidence(0.6f)
            .setMinTrackingConfidence(0.6f)
            .setResultListener { result, _ ->
                val currentTime = SystemClock.uptimeMillis()
                if (currentTime - lastProcessTime < minFrameInterval) return@setResultListener
                lastProcessTime = currentTime

                runOnUiThread { handOverlay.setResults(result, lensFacing == CameraSelector.LENS_FACING_FRONT) }

                val landmarksList = result.landmarks()
                if (landmarksList.isNotEmpty()) {
                    var rightHandCoords: FloatArray? = null
                    var leftHandCoords: FloatArray? = null
                    val handednessList = result.handednesses()
                    for (i in landmarksList.indices) {
                        val coords = extractHandCoords(landmarksList[i])
                        val handLabel = if (handednessList != null && i < handednessList.size && handednessList[i].isNotEmpty())
                            handednessList[i][0].categoryName() else if (i == 0) "Right" else "Left"
                        val isRight = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
                            handLabel == "Left" else handLabel == "Right"
                        if (isRight) rightHandCoords = coords else leftHandCoords = coords
                    }
                    lastLandmarks = rightHandCoords ?: leftHandCoords
                    // Secondary hand: the other one (if both detected), else null
                    lastSecondHand = if (rightHandCoords != null && leftHandCoords != null) {
                        if (lastLandmarks === rightHandCoords) leftHandCoords else rightHandCoords
                    } else null
                }
            }.build()
        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().setTargetResolution(Size(480, 640)).build()
                .also { it.setSurfaceProvider(findViewById<PreviewView>(R.id.previewView).surfaceProvider) }
            val analyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(240, 320))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
            analyzer.setAnalyzer(cameraExecutor) { image ->
                val currentTime = SystemClock.uptimeMillis()
                if (currentTime - lastProcessTime < minFrameInterval) { image.close(); return@setAnalyzer }
                try {
                    val bitmap = BitmapImageBuilder(image.toBitmap()).build()
                    handLandmarker.detectAsync(bitmap, currentTime)
                } catch (e: Exception) { Log.e("Camera", "Error", e) }
                finally { image.close() }
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(), preview, analyzer)
            } catch (e: Exception) { Log.e("CAMERA_ERROR", "${e.message}") }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handLandmarker.close()
    }
}
