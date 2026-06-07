package com.fyp.smartsigntranslator

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.View
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.os.SystemClock
import android.graphics.Color
import kotlin.math.sqrt
import kotlin.math.acos

data class QuizQuestion(
    val id: Int,
    val difficulty: String,
    val question: String,
    val image: String,
    val answers: List<String>,
    val wordsRequired: Int
)

class QuizActivity : AppCompatActivity() {

    private lateinit var handLandmarker: HandLandmarker
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var classifier: TFLiteClassifier

    // UI
    private lateinit var layoutModeSelect: ScrollView
    private lateinit var layoutDifficulty: ScrollView
    private lateinit var layoutSession: android.widget.LinearLayout
    private lateinit var layoutScore: ScrollView
    private lateinit var tvQuestion: TextView
    private lateinit var tvWordsRequired: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvLiveScore: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var answerChips: LinearLayout
    private lateinit var ivQuestionImage: ImageView

    // Quiz state
    private var currentDifficulty = "easy"
    private var allQuestions = listOf<QuizQuestion>()
    private var sessionQuestions = listOf<QuizQuestion>()
    private var currentQuestionIndex = 0
    private var totalScore = 0
    private val POINTS_PER_QUESTION = 10

    // Detection
    private val answerBuffer = mutableListOf<String>()
    private var consecutiveLabel = ""
    private var consecutiveCount = 0
    private val REQUIRED_CONSECUTIVE = 5
    private val WINDOW_SIZE = 8
    private val frameBuffer = mutableListOf<FloatArray>()
    private val secondHandBuffer = mutableListOf<FloatArray>()
    private var lastDetectionTime = 0L
    private val DETECTION_COOLDOWN = 1500L
    private val tfliteExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var lastProcessTime = 0L
    private val minFrameInterval = 66L  // ~15fps to reduce memory pressure

    // Double tap to clear
    private var lastTapTime = 0L
    private val DOUBLE_TAP_INTERVAL = 400L

    // Hover delete + submit
    private var deleteHoverStart = 0L
    private var submitHoverStart = 0L
    private val HOVER_DURATION = 1500L

    // Hand gone detection
    private var handMissingCounter = 0
    private val HAND_MISSING_THRESHOLD = 60
    private var hasCalledOnHandGone = false
    private var isCheckingAnswer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        classifier = TFLiteClassifier(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        bindViews()
        setupHandLandmarker()
        loadQuestions()
        applyTheme()

        findViewById<ImageButton>(R.id.btnBackQuiz).setOnClickListener {
            when {
                layoutSession.visibility == View.VISIBLE -> {
                    layoutSession.visibility = View.GONE
                    layoutDifficulty.visibility = View.VISIBLE
                    findViewById<android.widget.TextView>(R.id.tvQuizTitle).text = "Quiz Mode"
                    frameBuffer.clear()
                    secondHandBuffer.clear()
                    consecutiveLabel = ""
                    consecutiveCount = 0
                }
                layoutScore.visibility == View.VISIBLE -> {
                    layoutScore.visibility = View.GONE
                    layoutDifficulty.visibility = View.VISIBLE
                    findViewById<android.widget.TextView>(R.id.tvQuizTitle).text = "Quiz Mode"
                }
                layoutDifficulty.visibility == View.VISIBLE -> {
                    layoutDifficulty.visibility = View.GONE
                    layoutModeSelect.visibility = View.VISIBLE
                    findViewById<android.widget.TextView>(R.id.tvQuizTitle).text = "Practice & Game"
                }
                else -> {
                    finish()
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }
            }
        }

        // Difficulty buttons - adapt to theme
        val isDark = ThemeManager.isDark(this)
        fun makeDiffBg(darkBg: String, lightBg: String, strokeColor: String): android.graphics.drawable.GradientDrawable {
            val bg = android.graphics.drawable.GradientDrawable()
            bg.cornerRadius = 32f
            bg.setColor(android.graphics.Color.parseColor(if (isDark) darkBg else lightBg))
            bg.setStroke(4, android.graphics.Color.parseColor(strokeColor))
            return bg
        }
        val titleColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#1A1A1A")

        fun applyDiffButton(btnId: Int, darkBg: String, lightBg: String, stroke: String, subtitleDark: String, subtitleLight: String) {
            val btn = findViewById<LinearLayout>(btnId)
            btn.background = makeDiffBg(darkBg, lightBg, stroke)
            // Row 0: inner LinearLayout with title + subtitle
            val innerLayout = btn.getChildAt(0) as? LinearLayout
            (innerLayout?.getChildAt(0) as? android.widget.TextView)?.setTextColor(titleColor)
            (innerLayout?.getChildAt(1) as? android.widget.TextView)?.setTextColor(android.graphics.Color.parseColor(if (isDark) subtitleDark else subtitleLight))
            (btn.getChildAt(1) as? android.widget.ImageView)?.setColorFilter(titleColor)
        }

        findViewById<LinearLayout>(R.id.btnSelectQuiz).setOnClickListener {
            layoutModeSelect.visibility = View.GONE
            layoutDifficulty.visibility = View.VISIBLE
            findViewById<android.widget.TextView>(R.id.tvQuizTitle).text = "Quiz Mode"
        }
        findViewById<LinearLayout>(R.id.btnSelectGame).setOnClickListener {
            startActivity(android.content.Intent(this, GameActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnEasy).setOnClickListener { startQuiz("easy") }
        findViewById<LinearLayout>(R.id.btnNormal).setOnClickListener { startQuiz("normal") }
        findViewById<LinearLayout>(R.id.btnHard).setOnClickListener { startQuiz("hard") }
        applyDiffButton(R.id.btnEasy, "#0D2B1A", "#E8F5F0", "#10B981", "#CCFFD700", "#2D7A50")
        applyDiffButton(R.id.btnNormal, "#1A1A0D", "#FFF8E8", "#F59E0B", "#CC90EE90", "#8B6914")
        applyDiffButton(R.id.btnHard, "#2B0D0D", "#FFF0F0", "#EF4444", "#CCFF7F7F", "#C0392B")

        // Skip
        findViewById<AppCompatButton>(R.id.btnSkipQuestion).setOnClickListener { skipQuestion() }

        // Double tap camera box to clear all
        findViewById<android.widget.FrameLayout>(R.id.cameraBoxQuiz).setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime < DOUBLE_TAP_INTERVAL) {
                answerBuffer.clear()
                updateAnswerChips()
            }
            lastTapTime = now
        }

        // Score screen
        // Play Again
        findViewById<AppCompatButton>(R.id.btnPlayAgain).setOnClickListener {
            layoutScore.visibility = View.GONE
            layoutModeSelect.visibility = View.GONE
            layoutDifficulty.visibility = View.VISIBLE
            findViewById<android.widget.TextView>(R.id.tvQuizTitle).text = "Quiz Mode"
        }
        findViewById<AppCompatButton>(R.id.btnBackFromScore).setOnClickListener {
            layoutScore.visibility = View.GONE
            layoutDifficulty.visibility = View.GONE
            layoutModeSelect.visibility = View.VISIBLE
            findViewById<android.widget.TextView>(R.id.tvQuizTitle).text = "Practice & Game"
        }
    }

    private fun bindViews() {
        layoutModeSelect = findViewById(R.id.layoutModeSelect)
        layoutDifficulty = findViewById(R.id.layoutDifficulty)
        layoutSession = findViewById(R.id.layoutQuizSession)
        layoutScore = findViewById(R.id.layoutScoreScreen)
        tvQuestion = findViewById(R.id.tvQuestion)
        tvWordsRequired = findViewById(R.id.tvWordsRequired)
        tvConfidence = findViewById(R.id.tvConfidenceQuiz)
        tvProgress = findViewById(R.id.tvQuestionProgress)
        tvLiveScore = findViewById(R.id.tvLiveScore)
        progressBar = findViewById(R.id.progressBarQuiz)
        answerChips = findViewById(R.id.answerChips)
        ivQuestionImage = findViewById(R.id.ivQuestionImage)
    }

    private fun loadQuestions() {
        try {
            val json = assets.open("quiz_questions.json").bufferedReader().readText()
            val arr = JSONArray(json)
            val list = mutableListOf<QuizQuestion>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val answers = mutableListOf<String>()
                val ansArr = obj.getJSONArray("answer")
                for (j in 0 until ansArr.length()) answers.add(ansArr.getString(j).uppercase())
                list.add(QuizQuestion(
                    id = obj.getInt("id"),
                    difficulty = obj.getString("difficulty"),
                    question = obj.getString("question"),
                    image = obj.getString("image"),
                    answers = answers,
                    wordsRequired = obj.getInt("words_required")
                ))
            }
            allQuestions = list
            Log.d("QUIZ", "Loaded ${allQuestions.size} questions")
        } catch (e: Exception) {
            Log.e("QUIZ", "Error loading questions: ${e.message}")
        }
    }

    private fun startQuiz(difficulty: String) {
        currentDifficulty = difficulty
        val filtered = allQuestions.filter { it.difficulty == difficulty }.shuffled()
        sessionQuestions = filtered.take(10)
        currentQuestionIndex = 0
        totalScore = 0

        layoutDifficulty.visibility = View.GONE
        layoutSession.visibility = View.VISIBLE
        startCamera()
        showQuestion()

        // Show tutorial first time only
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("hasSeenQuizTutorial", false)) {
            window.decorView.postDelayed({ startTutorial() }, 1000)
        }
    }

    private fun showQuestion() {
        if (currentQuestionIndex >= sessionQuestions.size) {
            showScoreScreen()
            return
        }
        val q = sessionQuestions[currentQuestionIndex]
        answerBuffer.clear()
        frameBuffer.clear()
        secondHandBuffer.clear()
        consecutiveLabel = ""
        consecutiveCount = 0
        updateAnswerChips()

        tvQuestion.text = q.question
        tvWordsRequired.text = "${q.wordsRequired} perkataan diperlukan"
        tvProgress.text = "${currentQuestionIndex + 1} / ${sessionQuestions.size}"
        progressBar.progress = ((currentQuestionIndex + 1) * 100) / sessionQuestions.size
        tvLiveScore.text = "$totalScore pts"


        // Load image
        try {
            val stream = assets.open("quiz_images/${q.image}")
            val bmp = android.graphics.BitmapFactory.decodeStream(stream)
            ivQuestionImage.setImageBitmap(bmp)
        } catch (e: Exception) {
            ivQuestionImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }


    }


    private fun animateCorrect() {
        try {
            val cameraBox = findViewById<android.widget.FrameLayout>(R.id.cameraBoxQuiz) ?: return
            val resultOverlay = findViewById<android.widget.TextView>(R.id.tvResultOverlay) ?: return
            val originalBg = cameraBox.background

            val greenBorder = android.graphics.drawable.GradientDrawable()
            greenBorder.setStroke(6, android.graphics.Color.parseColor("#10B981"))
            greenBorder.setColor(android.graphics.Color.TRANSPARENT)
            cameraBox.background = greenBorder
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { cameraBox.background = originalBg } catch (e: Exception) {}
            }, 600)

            resultOverlay.scaleX = 0.5f
            resultOverlay.scaleY = 0.5f
            resultOverlay.alpha = 0f
            resultOverlay.visibility = android.view.View.VISIBLE
            resultOverlay.animate().scaleX(1.1f).scaleY(1.1f).alpha(1f).setDuration(250)
                .withEndAction { try { resultOverlay.animate().scaleX(1f).scaleY(1f).setDuration(100).start() } catch (e: Exception) {} }
                .start()
        } catch (e: Exception) { android.util.Log.e("QUIZ", "animateCorrect error: ${e.message}") }
    }

    private fun animateWrong() {
        try {
            val cameraBox = findViewById<android.widget.FrameLayout>(R.id.cameraBoxQuiz) ?: return
            val resultOverlay = findViewById<android.widget.TextView>(R.id.tvResultOverlay) ?: return
            val originalBg = cameraBox.background

            val redBorder = android.graphics.drawable.GradientDrawable()
            redBorder.setStroke(6, android.graphics.Color.parseColor("#EF4444"))
            redBorder.setColor(android.graphics.Color.TRANSPARENT)
            cameraBox.background = redBorder
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { cameraBox.background = originalBg } catch (e: Exception) {}
            }, 600)

            val shake = android.animation.ObjectAnimator.ofFloat(cameraBox, "translationX",
                0f, -20f, 20f, -16f, 16f, -10f, 10f, 0f)
            shake.duration = 400
            shake.start()

            resultOverlay.scaleX = 0.5f
            resultOverlay.scaleY = 0.5f
            resultOverlay.alpha = 0f
            resultOverlay.visibility = android.view.View.VISIBLE
            resultOverlay.animate().scaleX(1.1f).scaleY(1.1f).alpha(1f).setDuration(250)
                .withEndAction { try { resultOverlay.animate().scaleX(1f).scaleY(1f).setDuration(100).start() } catch (e: Exception) {} }
                .start()
        } catch (e: Exception) { android.util.Log.e("QUIZ", "animateWrong error: ${e.message}") }
    }

    private fun launchConfetti() {
        val root = findViewById<android.view.ViewGroup>(R.id.rootQuiz)
        val colors = listOf("#10B981", "#F59E0B", "#EF4444", "#3B82F6", "#FFFFFF")
        val random = java.util.Random()
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        repeat(30) {
            val confetti = android.view.View(this)
            val size = (8 + random.nextInt(12)).toFloat()
            val params = android.widget.FrameLayout.LayoutParams(size.toInt(), size.toInt())
            params.topMargin = -100
            confetti.layoutParams = params
            val bg = android.graphics.drawable.GradientDrawable()
            bg.shape = if (random.nextBoolean()) android.graphics.drawable.GradientDrawable.OVAL
            else android.graphics.drawable.GradientDrawable.RECTANGLE
            bg.setColor(android.graphics.Color.parseColor(colors[random.nextInt(colors.size)]))
            confetti.background = bg
            root.addView(confetti)

            val startX = random.nextInt(screenWidth).toFloat()
            val delay = random.nextInt(800).toLong()
            val duration = (1500 + random.nextInt(1000)).toLong()
            confetti.translationX = startX
            confetti.animate()
                .translationY((screenHeight + 100).toFloat())
                .translationX(startX + (random.nextInt(200) - 100).toFloat())
                .rotation((random.nextInt(360)).toFloat())
                .alpha(0.8f)
                .setStartDelay(delay)
                .setDuration(duration)
                .withEndAction { root.removeView(confetti) }
                .start()
        }
    }

    private fun makeChipBg(color: String): android.graphics.drawable.GradientDrawable {
        val bg = android.graphics.drawable.GradientDrawable()
        bg.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        bg.cornerRadius = 32f
        bg.setColor(android.graphics.Color.parseColor(color))
        return bg
    }

    private fun updateAnswerChips() {
        answerChips.removeAllViews()
        val dp = resources.displayMetrics.density
        answerBuffer.forEachIndexed { index, word ->
            val chip = TextView(this)
            chip.text = word
            chip.textSize = 14f
            chip.setTextColor(android.graphics.Color.WHITE)
            chip.setTypeface(null, android.graphics.Typeface.BOLD)
            chip.includeFontPadding = false
            chip.gravity = android.view.Gravity.CENTER
            chip.background = makeChipBg("#10B981")
            chip.setPadding((12*dp).toInt(), (4*dp).toInt(), (12*dp).toInt(), (4*dp).toInt())

            val lp = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                (32*dp).toInt()
            )
            lp.setMargins(0, 0, (8*dp).toInt(), 0)
            chip.layoutParams = lp

            chip.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> v.background = makeChipBg("#EF4444")
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.background = makeChipBg("#10B981")
                }
                false
            }

            chip.setOnLongClickListener {
                val scaleX = android.animation.ObjectAnimator.ofFloat(chip, "scaleX", 1f, 1.5f, 0f)
                val scaleY = android.animation.ObjectAnimator.ofFloat(chip, "scaleY", 1f, 1.5f, 0f)
                val alpha = android.animation.ObjectAnimator.ofFloat(chip, "alpha", 1f, 1f, 0f)
                scaleX.duration = 300; scaleY.duration = 300; alpha.duration = 300
                val set = android.animation.AnimatorSet()
                set.playTogether(scaleX, scaleY, alpha)
                set.start()
                set.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (index < answerBuffer.size) {
                            answerBuffer.removeAt(index)
                            updateAnswerChips()
                        }
                    }
                })
                true
            }
            answerChips.addView(chip)
        }
        // Auto scroll to latest word
        val scrollView = answerChips.parent as? android.widget.HorizontalScrollView
        scrollView?.post { scrollView.fullScroll(android.widget.HorizontalScrollView.FOCUS_RIGHT) }
    }

    private fun onWordDetected(word: String) {
        runOnUiThread {
            // Cancel pending submit
            submitCountdownRunnable?.let { submitCountdownHandler.removeCallbacks(it) }
            tvConfidence.text = ""
            tvConfidence.background = null
            answerBuffer.add(word)
            updateAnswerChips()
        }
    }

    private val submitCountdownHandler = Handler(Looper.getMainLooper())
    private var submitCountdownRunnable: Runnable? = null

    private fun onHandGone() {
        if (isCheckingAnswer) return
        if (answerBuffer.isEmpty()) return
        if (currentQuestionIndex >= sessionQuestions.size) return

        val q = sessionQuestions[currentQuestionIndex]
        if (answerBuffer.size < q.wordsRequired) return  // don't auto-submit if not enough words

        // Show countdown before submit
        var count = 3
        tvConfidence.text = "Submitting in $count..."
        tvConfidence.setTextColor(Color.YELLOW)
        tvConfidence.setPadding(16, 8, 16, 8)
        tvConfidence.setBackgroundColor(android.graphics.Color.parseColor("#CC000000"))

        submitCountdownRunnable?.let { submitCountdownHandler.removeCallbacks(it) }
        submitCountdownRunnable = object : Runnable {
            override fun run() {
                count--
                if (count > 0) {
                    tvConfidence.text = "Submitting in $count..."
                    submitCountdownHandler.postDelayed(this, 1000)
                } else {
                    tvConfidence.text = ""
                    tvConfidence.background = null
                    if (currentQuestionIndex < sessionQuestions.size && !isCheckingAnswer) {
                        checkAnswer()
                    }
                }
            }
        }
        submitCountdownHandler.postDelayed(submitCountdownRunnable!!, 1000)
    }

    private fun shakeWordBuffer() {
        val bufferView = findViewById<android.widget.LinearLayout>(R.id.layoutAnswerBuffer) ?: return
        val shake = android.animation.ObjectAnimator.ofFloat(bufferView, "translationX",
            0f, -15f, 15f, -12f, 12f, -8f, 8f, 0f)
        shake.duration = 350
        shake.start()
    }

    private fun checkAnswer() {
        if (answerBuffer.isEmpty()) return
        if (isCheckingAnswer) return
        if (currentQuestionIndex >= sessionQuestions.size) return

        val q = sessionQuestions[currentQuestionIndex]

        // Check minimum words required
        if (answerBuffer.size < q.wordsRequired) {
            // Shake and show message
            shakeWordBuffer()
            val prev = tvConfidence.text
            tvConfidence.text = "Need ${q.wordsRequired} words!"
            tvConfidence.setTextColor(Color.parseColor("#F59E0B"))
            tvConfidence.setPadding(16, 8, 16, 8)
            tvConfidence.setBackgroundColor(android.graphics.Color.parseColor("#CC000000"))
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tvConfidence.text = ""
                tvConfidence.background = null
            }, 1500)
            return
        }

        isCheckingAnswer = true
        val userAnswer = answerBuffer.joinToString(" ").uppercase().trim()
        val correct = q.answers.any { it.uppercase().trim() == userAnswer }

        findViewById<AppCompatButton>(R.id.btnSkipQuestion).isEnabled = false

        val resultOverlay = findViewById<android.widget.TextView>(R.id.tvResultOverlay)

        if (correct) {
            totalScore += POINTS_PER_QUESTION
            tvLiveScore.text = "$totalScore pts"
            resultOverlay.text = "✅ Correct!"
            resultOverlay.setTextColor(Color.parseColor("#10B981"))
            animateCorrect()
        } else {
            val wrongText = android.text.SpannableString("❌ Wrong!  ${q.answers[0]}")
            wrongText.setSpan(android.text.style.ForegroundColorSpan(Color.RED), 0, 9, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            wrongText.setSpan(android.text.style.ForegroundColorSpan(Color.YELLOW), 9, wrongText.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            resultOverlay.text = wrongText
            animateWrong()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            resultOverlay.visibility = android.view.View.GONE
            currentQuestionIndex++
            tvConfidence.text = ""
            isCheckingAnswer = false
            hasCalledOnHandGone = false
            handMissingCounter = 0
            showQuestion()
            findViewById<AppCompatButton>(R.id.btnSkipQuestion).isEnabled = true
        }, 1500)
    }

    private fun skipQuestion() {
        val q = sessionQuestions[currentQuestionIndex]
        val resultOverlay = findViewById<android.widget.TextView>(R.id.tvResultOverlay)
        resultOverlay.text = "💡 ${q.answers[0]}"
        resultOverlay.setTextColor(Color.YELLOW)
        resultOverlay.visibility = android.view.View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            resultOverlay.visibility = android.view.View.GONE
            currentQuestionIndex++
            tvConfidence.text = ""
            showQuestion()
        }, 1500)
    }

    private fun showScoreScreen() {
        layoutSession.visibility = View.GONE
        layoutScore.visibility = View.VISIBLE

        val percent = (totalScore * 100) / (sessionQuestions.size * POINTS_PER_QUESTION)
        findViewById<TextView>(R.id.tvScorePoints).text = "$totalScore / ${sessionQuestions.size * POINTS_PER_QUESTION}"
        if (percent == 100) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                launchConfetti()
            }, 500)
        }

        val (emoji, title, message) = when {
            percent == 100 -> Triple("🏆", "Sempurna!", "Perfect score! You mastered all questions!")
            percent >= 80 -> Triple("🎉", "Excellent!", "Excellent performance! Keep it up!")
            percent >= 60 -> Triple("👍", "Good job!", "Good effort! Try again to do better!")
            percent >= 40 -> Triple("💪", "Keep going!", "Don't give up! Practice makes perfect!")
            else -> Triple("📚", "Need Practice", "Don't worry! Try again and you'll improve!")
        }

        findViewById<TextView>(R.id.tvScoreEmoji).text = emoji
        findViewById<TextView>(R.id.tvScoreTitle).text = title
        findViewById<TextView>(R.id.tvScoreMessage).text = message
    }

    private fun applyTheme() {
        val root = findViewById<ConstraintLayout>(R.id.rootQuiz)
        ThemeManager.apply(activity = this, root = root)
    }

    // ── Camera & Detection ──────────────────────────────────────────────

    private fun computeCurlFeatures(coords: FloatArray): FloatArray {
        val fingerJoints = arrayOf(
            Triple(1, 2, 3), Triple(5, 6, 7), Triple(9, 10, 11),
            Triple(13, 14, 15), Triple(17, 18, 19)
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
                val currentTime = SystemClock.uptimeMillis()
                if (currentTime - lastProcessTime < minFrameInterval) return@setResultListener
                lastProcessTime = currentTime

                runOnUiThread {
                    findViewById<HandOverlayView>(R.id.handOverlayQuiz)
                        .setResults(result, lensFacing == CameraSelector.LENS_FACING_FRONT)
                }

                val landmarksList = result.landmarks()
                if (landmarksList.isEmpty()) {
                    frameBuffer.clear()
                    secondHandBuffer.clear()
                    consecutiveLabel = ""
                    consecutiveCount = 0
                    handMissingCounter++
                    if (handMissingCounter >= HAND_MISSING_THRESHOLD && !hasCalledOnHandGone) {
                        hasCalledOnHandGone = true
                        runOnUiThread { onHandGone() }
                    }
                    return@setResultListener
                }
                if (!isCheckingAnswer) {
                    handMissingCounter = 0
                    hasCalledOnHandGone = false
                    submitCountdownRunnable?.let { submitCountdownHandler.removeCallbacks(it) }
                    runOnUiThread {
                        if (tvConfidence.text.toString().startsWith("Submitting")) {
                            tvConfidence.text = ""
                            tvConfidence.background = null
                        }
                    }
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

                // Hover delete button — bottom-left of camera
                val rawLm = landmarksList[0]
                val tipX = rawLm[8].x()
                val tipY = rawLm[8].y()
                val mirX = if (lensFacing == CameraSelector.LENS_FACING_FRONT) 1f - tipX else tipX
                val mirY = if (lensFacing == CameraSelector.LENS_FACING_FRONT) 1f - tipY else tipY
                val inDeleteBtn = mirX in 0.75f..1.00f && mirY in 0.00f..0.25f
                val inSubmitBtn = mirX in 0.05f..0.30f && mirY in 0.80f..1.00f
                android.util.Log.d("HOVER_QUIZ", "x=${"%.3f".format(mirX)} y=${"%.3f".format(mirY)} submit=$inSubmitBtn delete=$inDeleteBtn")
                val nowHover = System.currentTimeMillis()

                if (inSubmitBtn) {
                    if (submitHoverStart == 0L) submitHoverStart = nowHover
                    val elapsed = nowHover - submitHoverStart
                    val progress = (elapsed.toFloat() / HOVER_DURATION).coerceIn(0f, 1f)
                    runOnUiThread {
                        val btn = findViewById<android.widget.FrameLayout>(R.id.btnHoverSubmitQuiz)
                        btn?.alpha = 0.6f + (progress * 0.4f)
                        btn?.scaleX = 1f + (progress * 0.1f)
                        btn?.scaleY = 1f + (progress * 0.1f)
                    }
                    if (elapsed >= HOVER_DURATION) {
                        submitHoverStart = 0L
                        runOnUiThread {
                            val btn = findViewById<android.widget.FrameLayout>(R.id.btnHoverSubmitQuiz)
                            btn?.alpha = 1f; btn?.scaleX = 1f; btn?.scaleY = 1f
                            if (answerBuffer.isNotEmpty() && !isCheckingAnswer) {
                                submitCountdownRunnable?.let { submitCountdownHandler.removeCallbacks(it) }
                                tvConfidence.text = ""
                                tvConfidence.background = null
                                checkAnswer()
                            }
                        }
                    }
                    return@setResultListener  // pause detection while hovering submit
                } else {
                    if (submitHoverStart > 0L) {
                        submitHoverStart = 0L
                        runOnUiThread {
                            val btn = findViewById<android.widget.FrameLayout>(R.id.btnHoverSubmitQuiz)
                            btn?.alpha = 0.7f; btn?.scaleX = 1f; btn?.scaleY = 1f
                        }
                    }
                }

                if (inDeleteBtn) {
                    if (deleteHoverStart == 0L) deleteHoverStart = nowHover
                    if (nowHover - deleteHoverStart >= HOVER_DURATION) {
                        deleteHoverStart = 0L
                        runOnUiThread {
                            if (answerBuffer.isNotEmpty()) {
                                answerBuffer.removeLastOrNull()
                                updateAnswerChips()
                            }
                        }
                    }
                    return@setResultListener
                } else {
                    deleteHoverStart = 0L
                }

                frameBuffer.add(coordsToUse)
                secondHandBuffer.add(secondHand)
                if (frameBuffer.size > WINDOW_SIZE) {
                    frameBuffer.removeAt(0)
                    secondHandBuffer.removeAt(0)
                }

                if (frameBuffer.size == WINDOW_SIZE) {
                    tfliteExecutor.execute {
                        val startCurl = computeCurlFeatures(frameBuffer[0])
                        val endCurl = computeCurlFeatures(frameBuffer.last())
                        val input = secondHandBuffer[0] + frameBuffer[0] + startCurl + secondHandBuffer.last() + frameBuffer.last() + endCurl
                        val (label, confidence) = classifier.predict(input)

                        // Skip single letters — fingerspelling not needed in quiz
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
                                        runOnUiThread {
                                            tvConfidence.text = "${"%.0f".format(confidence * 100)}%"
                                            onWordDetected(label.uppercase())
                                        }
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
                .also { it.setSurfaceProvider(findViewById<PreviewView>(R.id.previewViewQuiz).surfaceProvider) }
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
                } catch (e: Exception) { Log.e("QUIZ", "Camera error", e) }
                finally { image.close() }
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(), preview, analyzer)
            } catch (e: Exception) { Log.e("QUIZ", "Camera bind error", e) }
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
                targetRect = getRect(R.id.cameraBoxQuiz),
                tooltipText = "Sign the BIM gesture shown in the question",
                tooltipPosition = TooltipPosition.BELOW
            ),
            TutorialStep(
                targetRect = run {
                    val v = findViewById<android.view.View>(R.id.ivQuestionImage)
                    val card = v?.parent as? android.view.View ?: v
                    val loc = IntArray(2)
                    card?.getLocationOnScreen(loc)
                    val p = (8 * dp)
                    android.graphics.RectF(loc[0]-p, loc[1]-p, loc[0]+(card?.width?:0)+p, loc[1]+(card?.height?:0)+p)
                },
                tooltipText = "Read the question and sign the correct BIM answer",
                tooltipPosition = TooltipPosition.BELOW
            ),
            TutorialStep(
                targetRect = run {
                    val v = findViewById<android.view.View>(R.id.answerChips)
                    val card = v?.parent?.parent as? android.view.View ?: v
                    val loc = IntArray(2)
                    card?.getLocationOnScreen(loc)
                    val p = (8 * dp)
                    android.graphics.RectF(loc[0]-p, loc[1]-p, loc[0]+(card?.width?:0)+p, loc[1]+(card?.height?:0)+p)
                },
                tooltipText = "Your detected words appear here. Long press any word to remove it",
                tooltipPosition = TooltipPosition.ABOVE
            ),
            TutorialStep(
                targetRect = getRect(R.id.btnHoverSubmitQuiz),
                tooltipText = "Hover your hand here for 1.5s to submit your answer",
                tooltipPosition = TooltipPosition.ABOVE
            ),
            TutorialStep(
                targetRect = getRect(R.id.btnHoverDeleteQuiz),
                tooltipText = "Hover here for 1.5s to delete the last word",
                tooltipPosition = TooltipPosition.ABOVE
            )
        )

        TutorialManager(this, steps) {
            getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                .edit().putBoolean("hasSeenQuizTutorial", true).apply()
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tfliteExecutor.shutdown()
        handLandmarker.close()
        classifier.close()
    }
}