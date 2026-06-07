package com.fyp.smartsigntranslator

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.*
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.acos
import kotlin.math.sqrt

class GameActivity : AppCompatActivity() {

    private lateinit var handLandmarker: HandLandmarker
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var classifier: TFLiteClassifier

    // UI
    private lateinit var layoutStart: LinearLayout
    private lateinit var layoutSession: LinearLayout
    private lateinit var layoutGameOver: ScrollView
    private lateinit var tvGameScore: TextView
    private lateinit var tvMultiplier: TextView
    private lateinit var tvTargetWord: TextView
    private lateinit var tvTimer: TextView
    private lateinit var circularTimer: CircularTimerView
    private lateinit var tvStrike1: TextView
    private lateinit var tvStrike2: TextView
    private lateinit var tvStrike3: TextView
    private lateinit var tvGameResult: TextView
    private lateinit var tvFinalScore: TextView
    private lateinit var tvGameOverTitle: TextView
    private lateinit var tvGameOverEmoji: TextView
    private lateinit var etPlayerName: EditText
    private lateinit var leaderboardContainer: LinearLayout

    // Game state
    private val ALL_LABELS = arrayOf(
        "ABANG","AIR","AMBIL","ANJING","ARNAB","AWAK","AYAH","BACA","BAGAIMANA",
        "BELAJAR","BELI","BERAPA","BERI","BILA","BUKU","DAN","DENGAN","DOBI",
        "EMAK","FERI","HAI","HOSPITAL","HOTEL","INI","KAMI","KAMU","KATIL",
        "KE","KEDAI","KENAPA","KERJA","KUCING","LEMBU","LIHAT","LUKIS","MAAF",
        "MAIN","MAJALAH","MAKAN","MANA","MANDI","MILO","MINUM","MOTOSIKAL",
        "NASI","PEJABAT","PERGI","POTONG","PUKUL","ROTI","RUMAH","SAYA",
        "SEKOLAH","SIAPA","TEH","TELUR","TERIMA KASIH","TIDUR"
    )
    private var lastTarget = ""

    private var currentTarget = ""
    private var score = 0
    private var strikes = 0
    private var consecutiveCorrect = 0
    private var isMultiplierActive = false
    private var timeLeft = 60
    private var isGameRunning = false
    private var isShowingResult = false

    // Detection
    private val frameBuffer = mutableListOf<FloatArray>()
    private val secondHandBuffer = mutableListOf<FloatArray>()
    private var consecutiveLabel = ""
    private var consecutiveCount = 0
    private val REQUIRED_CONSECUTIVE = 5
    private val WINDOW_SIZE = 8
    private var lastDetectionTime = 0L
    private val DETECTION_COOLDOWN = 1500L
    private val tfliteExecutor = Executors.newSingleThreadExecutor()
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var lastProcessTime = 0L
    private val minFrameInterval = 66L

    // Timer
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        classifier = TFLiteClassifier(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        prefs = getSharedPreferences("GameLeaderboard", Context.MODE_PRIVATE)

        bindViews()
        applyTheme()
        val bg = if (ThemeManager.isDark(this)) "#000000" else "#FAF9F6"
        findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rootGame)
            .setBackgroundColor(android.graphics.Color.parseColor(bg))

        findViewById<ImageButton>(R.id.btnBackGame).setOnClickListener { finish() }
        findViewById<AppCompatButton>(R.id.btnStartGame).setOnClickListener { startGame() }
        findViewById<AppCompatButton>(R.id.btnSaveAndPlayAgain).setOnClickListener { saveAndRestart() }
        // btnSaveScore handled in showGameOverScreen
        findViewById<AppCompatButton>(R.id.btnGameExit).setOnClickListener {
            saveScore()
            finish()
        }
    }

    private fun bindViews() {
        layoutStart = findViewById(R.id.layoutGameStart)
        layoutSession = findViewById(R.id.layoutGameSession)
        layoutGameOver = findViewById(R.id.layoutGameOver)
        tvGameScore = findViewById(R.id.tvGameScore)
        tvMultiplier = findViewById(R.id.tvMultiplier)
        tvTargetWord = findViewById(R.id.tvTargetWord)
        tvTimer = findViewById(R.id.tvTimer)
        circularTimer = findViewById(R.id.circularTimer)
        tvStrike1 = findViewById(R.id.tvStrike1)
        tvStrike2 = findViewById(R.id.tvStrike2)
        tvStrike3 = findViewById(R.id.tvStrike3)
        tvGameResult = findViewById(R.id.tvGameResult)
        tvFinalScore = findViewById(R.id.tvFinalScore)
        tvGameOverTitle = findViewById(R.id.tvGameOverTitle)
        tvGameOverEmoji = findViewById(R.id.tvGameOverEmoji)
        etPlayerName = findViewById(R.id.etPlayerName)
        leaderboardContainer = findViewById(R.id.leaderboardContainer)
    }

    private fun startGame() {
        score = 0
        strikes = 0
        consecutiveCorrect = 0
        isMultiplierActive = false
        timeLeft = 60
        isGameRunning = true
        isShowingResult = false

        resetStrikes()
        tvGameScore.text = "0 pts"
        tvMultiplier.visibility = View.GONE
        circularTimer.updateProgress(1f)

        layoutStart.visibility = View.GONE
        layoutSession.visibility = View.VISIBLE

        setupHandLandmarker()
        startCamera()
        nextTarget()
        startTimer()

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isGameRunning && !isShowingResult) {
                    showResult("⏭ Skipped!", Color.parseColor("#F59E0B"))
                    nextTarget()
                }
                return true
            }
        })
        findViewById<FrameLayout>(R.id.cameraBoxGame).setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            v.performClick()
            true
        }

        // Show tutorial first time only
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("hasSeenGameTutorial", false)) {
            window.decorView.postDelayed({ startTutorial() }, 1000)
        }
    }

    private fun nextTarget() {
        var next = ALL_LABELS.random()
        while (next == lastTarget) next = ALL_LABELS.random()
        lastTarget = next
        currentTarget = next
        runOnUiThread {
            tvTargetWord.text = currentTarget
            tvTargetWord.alpha = 0f
            tvTargetWord.animate().alpha(1f).setDuration(200).start()
        }
        frameBuffer.clear()
        consecutiveLabel = ""
        consecutiveCount = 0
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isGameRunning) return
                timeLeft--
                tvTimer.text = timeLeft.toString()
                circularTimer.updateProgress(timeLeft / 60f)

                if (timeLeft <= 10) {
                    tvTimer.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200)
                        .withEndAction { tvTimer.animate().scaleX(1f).scaleY(1f).setDuration(200).start() }
                        .start()
                }

                if (timeLeft <= 0) {
                    gameOver(false)
                } else {
                    timerHandler.postDelayed(this, 1000)
                }
            }
        }
        timerHandler.postDelayed(timerRunnable!!, 1000)
    }

    private fun onGestureDetected(label: String) {
        if (!isGameRunning || isShowingResult) return

        val correct = label.uppercase() == currentTarget.uppercase()

        if (correct) {
            consecutiveCorrect++
            if (consecutiveCorrect >= 5) {
                isMultiplierActive = true
                tvMultiplier.visibility = View.VISIBLE
                tvMultiplier.text = "🔥 ×2"
            }
            val points = if (isMultiplierActive) 20 else 10
            score += points
            tvGameScore.text = "$score pts"
            showResult("✅ +$points!", Color.parseColor("#10B981"))
            nextTarget()
        } else {
            consecutiveCorrect = 0
            isMultiplierActive = false
            tvMultiplier.visibility = View.GONE
            strikes++
            updateStrikes()
            showResult("❌ Wrong!", Color.parseColor("#EF4444"))
            nextTarget()

            if (strikes >= 3) {
                gameOver(true)
                return
            }
        }
    }

    private fun showResult(text: String, color: Int) {
        isShowingResult = true
        tvGameResult.text = text
        tvGameResult.setTextColor(color)
        tvGameResult.visibility = View.VISIBLE
        tvGameResult.alpha = 1f
        tvGameResult.animate().alpha(0f).setDuration(1500).withEndAction {
            tvGameResult.visibility = View.GONE
            isShowingResult = false
        }.start()
    }

    private fun updateStrikes() {
        val strikes = listOf(tvStrike1, tvStrike2, tvStrike3)
        for (i in 0 until this.strikes.coerceAtMost(3)) {
            strikes[i].setTextColor(Color.parseColor("#EF4444"))
            strikes[i].animate().scaleX(1.3f).scaleY(1.3f).setDuration(150)
                .withEndAction { strikes[i].animate().scaleX(1f).scaleY(1f).setDuration(150).start() }
                .start()
        }
    }

    private fun resetStrikes() {
        listOf(tvStrike1, tvStrike2, tvStrike3).forEach {
            it.setTextColor(Color.parseColor("#3A3A3A"))
        }
    }

    private fun gameOver(threeStrikes: Boolean) {
        isGameRunning = false
        timerRunnable?.let { timerHandler.removeCallbacks(it) }

        runOnUiThread {
            if (threeStrikes) {
                showStrikesAnimation {
                    showGameOverScreen(threeStrikes)
                }
            } else {
                showTimeUpAnimation {
                    showGameOverScreen(threeStrikes)
                }
            }
        }
    }

    private fun showTimeUpAnimation(onDone: () -> Unit) {
        val overlay = android.widget.TextView(this)
        overlay.text = "TIME'S UP!"
        overlay.textSize = 40f
        overlay.setTextColor(Color.WHITE)
        overlay.gravity = android.view.Gravity.CENTER
        overlay.setTypeface(null, android.graphics.Typeface.BOLD)
        overlay.alpha = 0f
        overlay.setBackgroundColor(Color.parseColor("#CC000000"))
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        val cameraBox = findViewById<android.widget.FrameLayout>(R.id.cameraBoxGame)
        cameraBox.addView(overlay, params)
        overlay.animate().alpha(1f).setDuration(2000).withEndAction {
            Handler(Looper.getMainLooper()).postDelayed({
                cameraBox.removeView(overlay)
                onDone()
            }, 500)
        }.start()
    }

    private fun showStrikesAnimation(onDone: () -> Unit) {
        val cameraBox = findViewById<android.widget.FrameLayout>(R.id.cameraBoxGame)

        val xOverlay = android.widget.TextView(this)
        xOverlay.text = "GAME OVER"
        xOverlay.textSize = 40f
        xOverlay.setTextColor(Color.WHITE)
        xOverlay.gravity = android.view.Gravity.CENTER
        xOverlay.setTypeface(null, android.graphics.Typeface.BOLD)
        xOverlay.setBackgroundColor(Color.parseColor("#CC000000"))
        xOverlay.setPadding(0, 32, 0, 32)
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = android.view.Gravity.CENTER
        tvGameResult.visibility = View.GONE


        xOverlay.alpha = 0f
        cameraBox.addView(xOverlay, params)
        xOverlay.animate().alpha(1f).setDuration(1500).withEndAction {
            Handler(Looper.getMainLooper()).postDelayed({
                xOverlay.animate().alpha(0f).setDuration(1500).withEndAction {
                    cameraBox.removeView(xOverlay)
                    onDone()
                }.start()
            }, 2000)
        }.start()
    }

    private fun showGameOverScreen(threeStrikes: Boolean) {
        layoutSession.visibility = View.GONE
        layoutGameOver.visibility = View.VISIBLE

        etPlayerName.text.clear()


        tvGameOverEmoji.visibility = View.VISIBLE
        tvGameOverTitle.visibility = View.VISIBLE
        tvFinalScore.visibility = View.VISIBLE

        tvFinalScore.text = "$score pts"
        tvGameOverTitle.text = if (threeStrikes) "3 Strikes!" else "Time's Up!"


        val entries = getLeaderboard()
        val qualifies = entries.size < 5 || score > (entries.lastOrNull()?.second ?: 0)

        val nameSection = findViewById<android.widget.LinearLayout>(R.id.nameSectionGame)
        val btnSave = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnSaveScore)
        val btnPlayAgain = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnSaveAndPlayAgain)
        val btnExit = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnGameExit)


        tvGameOverEmoji.text = if (threeStrikes) "✕ ✕ ✕" else "⏰"
        tvGameOverEmoji.textSize = if (threeStrikes) 80f else 72f
        tvGameOverEmoji.setTextColor(if (threeStrikes) Color.parseColor("#EF4444") else Color.WHITE)

        val leaderboardSection = findViewById<android.widget.LinearLayout>(R.id.leaderboardSection)
        val congratsSection = findViewById<android.widget.LinearLayout>(R.id.congratsSection)

        if (qualifies) {

            nameSection.visibility = View.VISIBLE
            btnSave.visibility = View.VISIBLE
            btnPlayAgain.visibility = View.GONE
            leaderboardSection.visibility = View.GONE
            congratsSection.visibility = View.GONE

            btnSave.setOnClickListener {
                saveScore()

                nameSection.visibility = View.GONE
                btnSave.visibility = View.GONE
                tvGameOverEmoji.visibility = View.GONE
                tvGameOverTitle.visibility = View.GONE
                tvFinalScore.visibility = View.GONE
                congratsSection.visibility = View.VISIBLE
                leaderboardSection.visibility = View.VISIBLE
                btnPlayAgain.visibility = View.VISIBLE
                loadLeaderboard()
            }
        } else {

            nameSection.visibility = View.GONE
            btnSave.visibility = View.GONE
            btnPlayAgain.visibility = View.VISIBLE
            congratsSection.visibility = View.GONE
            leaderboardSection.visibility = View.VISIBLE
            loadLeaderboard()
        }
    }

    private fun saveScore() {
        val name = etPlayerName.text.toString().trim().ifEmpty { "Anonymous" }
        val entries = getLeaderboard().toMutableList()
        entries.add(Pair(name, score))
        entries.sortByDescending { it.second }
        val top5 = entries.take(5)

        val arr = JSONArray()
        top5.forEach {
            val obj = JSONObject()
            obj.put("name", it.first)
            obj.put("score", it.second)
            arr.put(obj)
        }
        prefs.edit().putString("leaderboard", arr.toString()).apply()
    }

    private fun saveAndRestart() {
        layoutGameOver.visibility = View.GONE
        layoutStart.visibility = View.VISIBLE
    }

    private fun getLeaderboard(): List<Pair<String, Int>> {
        val json = prefs.getString("leaderboard", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<Pair<String, Int>>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(Pair(obj.getString("name"), obj.getInt("score")))
        }
        return list
    }

    private fun loadLeaderboard() {
        leaderboardContainer.removeAllViews()
        val entries = getLeaderboard()
        val medals = listOf("🥇", "🥈", "🥉", "4.", "5.")

        if (entries.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No scores yet. Be the first!"
            tv.setTextColor(Color.parseColor("#71717A"))
            tv.textSize = 13f
            leaderboardContainer.addView(tv)
            return
        }

        val dp = resources.displayMetrics.density
        entries.forEachIndexed { i, (name, pts) ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (10 * dp).toInt()
            row.layoutParams = lp

            val medalText = when(i) {
                0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"
                else -> "${i+1}."
            }
            val medal = TextView(this).apply {
                text = medalText
                textSize = if (i < 3) 18f else 14f
                setTextColor(if (i < 3) Color.WHITE else if (ThemeManager.isDark(this@GameActivity)) Color.WHITE else Color.parseColor("#1A1A1A"))
                val p = LinearLayout.LayoutParams((48 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                p.marginEnd = (8 * dp).toInt()
                layoutParams = p
            }

            val isDark = ThemeManager.isDark(this)
            val nameView = TextView(this).apply {
                text = name
                textSize = 14f
                setTextColor(if (i == 0) Color.parseColor("#F59E0B") else if (isDark) Color.WHITE else Color.parseColor("#1A1A1A"))
                setTypeface(null, if (i == 0) Typeface.BOLD else Typeface.NORMAL)
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = p
            }

            val scoreView = TextView(this).apply {
                text = "$pts pts"
                textSize = 14f
                setTextColor(Color.parseColor("#10B981"))
                setTypeface(null, Typeface.BOLD)
            }

            row.addView(medal)
            row.addView(nameView)
            row.addView(scoreView)
            leaderboardContainer.addView(row)
        }
    }

    private fun applyTheme() {
        val root = findViewById<ConstraintLayout>(R.id.rootGame)
        ThemeManager.apply(activity = this, root = root)
    }

    // ── Camera & Detection

    private fun computeCurlFeatures(coords: FloatArray): FloatArray {
        val fingerJoints = arrayOf(
            Triple(1,2,3), Triple(5,6,7), Triple(9,10,11),
            Triple(13,14,15), Triple(17,18,19)
        )
        return FloatArray(5) { i ->
            val (base, mid, tip) = fingerJoints[i]
            val bx = coords[base*3]; val by = coords[base*3+1]; val bz = coords[base*3+2]
            val mx = coords[mid*3];  val my = coords[mid*3+1];  val mz = coords[mid*3+2]
            val tx = coords[tip*3];  val ty = coords[tip*3+1];  val tz = coords[tip*3+2]
            val v1x = bx-mx; val v1y = by-my; val v1z = bz-mz
            val v2x = tx-mx; val v2y = ty-my; val v2z = tz-mz
            val dot = v1x*v2x + v1y*v2y + v1z*v2z
            val mag = sqrt((v1x*v1x+v1y*v1y+v1z*v1z).toDouble()) *
                    sqrt((v2x*v2x+v2y*v2y+v2z*v2z).toDouble()) + 1e-8
            acos((dot/mag).coerceIn(-1.0, 1.0)).toFloat()
        }
    }

    private fun extractHandCoords(lm: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): FloatArray {
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
                if (!isGameRunning) return@setResultListener
                val currentTime = SystemClock.uptimeMillis()
                if (currentTime - lastProcessTime < minFrameInterval) return@setResultListener
                lastProcessTime = currentTime

                runOnUiThread {
                    findViewById<HandOverlayView>(R.id.handOverlayGame)
                        .setResults(result, lensFacing == CameraSelector.LENS_FACING_FRONT)
                }

                val landmarksList = result.landmarks()
                if (landmarksList.isEmpty()) {
                    frameBuffer.clear(); secondHandBuffer.clear(); consecutiveLabel = ""; consecutiveCount = 0
                    return@setResultListener
                }


                var rightHandCoords: FloatArray? = null
                var leftHandCoords: FloatArray? = null
                val handednessList = result.handednesses()
                for (i in landmarksList.indices) {
                    val coords = extractHandCoords(landmarksList[i])
                    val handLabel = if (handednessList != null && i < handednessList.size && handednessList[i].isNotEmpty())
                        handednessList[i][0].categoryName() else if (i == 0) "Right" else "Left"
                    val isRight = if (lensFacing == CameraSelector.LENS_FACING_FRONT) handLabel == "Left" else handLabel == "Right"
                    if (isRight) rightHandCoords = coords else leftHandCoords = coords
                }
                val coordsToUse = rightHandCoords ?: leftHandCoords ?: return@setResultListener
                val secondHand = if (rightHandCoords != null && leftHandCoords != null) {
                    if (rightHandCoords === coordsToUse) leftHandCoords else rightHandCoords
                } else FloatArray(63)

                frameBuffer.add(coordsToUse)
                secondHandBuffer.add(secondHand)
                if (frameBuffer.size > WINDOW_SIZE) {
                    frameBuffer.removeAt(0)
                    secondHandBuffer.removeAt(0)
                }

                if (frameBuffer.size == WINDOW_SIZE) {
                    tfliteExecutor.execute {
                        val startFrame = frameBuffer[0]
                        val endFrame = frameBuffer.last()
                        val startCurl = computeCurlFeatures(startFrame)
                        val endCurl = computeCurlFeatures(endFrame)
                        val input = secondHandBuffer[0] + startFrame + startCurl + secondHandBuffer.last() + endFrame + endCurl
                        val (label, confidence) = classifier.predict(input)

                        // Skip single letters
                        if (label.length == 1 && label[0].isLetter()) return@execute

                        val sharedPref = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                        val threshold = sharedPref.getInt("sensitivity", 95) / 100f

                        if (confidence > threshold) {
                            if (label == consecutiveLabel) {
                                consecutiveCount++
                                if (consecutiveCount >= REQUIRED_CONSECUTIVE) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastDetectionTime >= DETECTION_COOLDOWN) {
                                        lastDetectionTime = now
                                        consecutiveLabel = ""
                                        consecutiveCount = 0
                                        runOnUiThread { onGestureDetected(label) }
                                    }
                                }
                            } else {
                                consecutiveLabel = label
                                consecutiveCount = 1
                            }
                        } else {
                            consecutiveLabel = ""
                            consecutiveCount = 0
                        }
                    }
                }
            }.build()
        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().setTargetResolution(Size(480, 640)).build()
                .also { it.setSurfaceProvider(findViewById<PreviewView>(R.id.previewViewGame).surfaceProvider) }
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
                } catch (e: Exception) { Log.e("GAME", "Camera error", e) }
                finally { image.close() }
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(), preview, analyzer)
            } catch (e: Exception) { Log.e("GAME", "Camera bind error", e) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startTutorial() {
        val dp = resources.displayMetrics.density

        fun getRect(viewId: Int): android.graphics.RectF {
            val v = findViewById<android.view.View>(viewId) ?: return android.graphics.RectF()
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val p = (8 * dp)
            return android.graphics.RectF(loc[0]-p, loc[1]-p, loc[0]+v.width+p, loc[1]+v.height+p)
        }

        val steps = listOf(
            TutorialStep(
                targetRect = getRect(R.id.cameraBoxGame),
                tooltipText = "Sign the BIM gesture shown as fast as you can!\n\n💡 Double tap the screen to skip a word",
                tooltipPosition = TooltipPosition.BELOW
            ),
            TutorialStep(
                targetRect = getRect(R.id.tvTargetWord),
                tooltipText = "This is the word you need to sign",
                tooltipPosition = TooltipPosition.ABOVE
            ),
            TutorialStep(
                targetRect = getRect(R.id.circularTimer),
                tooltipText = "Race against the clock! Sign as many words as you can before time runs out",
                tooltipPosition = TooltipPosition.ABOVE
            ),
            TutorialStep(
                targetRect = run {
                    val s1 = findViewById<android.view.View>(R.id.tvStrike1)
                    val s3 = findViewById<android.view.View>(R.id.tvStrike3)
                    val loc1 = IntArray(2); s1?.getLocationOnScreen(loc1)
                    val loc3 = IntArray(2); s3?.getLocationOnScreen(loc3)
                    val p = (8 * dp)
                    android.graphics.RectF(loc1[0]-p, loc1[1]-p, loc3[0]+(s3?.width?:0)+p, loc1[1]+(s1?.height?:0)+p)
                },
                tooltipText = "3 strikes and game over! Wrong gesture = 1 strike",
                tooltipPosition = TooltipPosition.ABOVE
            ),
            TutorialStep(
                targetRect = getRect(R.id.tvGameScore),
                tooltipText = "Each correct sign earns +10pts. Hit 5 in a row to activate ×2 multiplier — worth +20pts each!",
                tooltipPosition = TooltipPosition.BELOW
            )
        )

        TutorialManager(this, steps) {
            getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                .edit().putBoolean("hasSeenGameTutorial", true).apply()
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        isGameRunning = false
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        cameraExecutor.shutdown()
        tfliteExecutor.shutdown()
        try { handLandmarker.close() } catch (e: Exception) {}
        classifier.close()
    }
}