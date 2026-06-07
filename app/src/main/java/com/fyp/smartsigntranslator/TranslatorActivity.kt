package com.fyp.smartsigntranslator

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import android.widget.LinearLayout
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.FrameLayout

class TranslatorActivity : AppCompatActivity(), TextToSpeech.OnInitListener, SensorEventListener {
    private lateinit var handLandmarker: HandLandmarker
    private lateinit var handOverlay: HandOverlayView
    private lateinit var gestureText: TextView
    private lateinit var bufferText: TextView
    private lateinit var sentenceText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var classifier: TFLiteClassifier

    private var tts: TextToSpeech? = null
    private val frameBuffer = mutableListOf<FloatArray>()
    private val secondHandBuffer = mutableListOf<FloatArray>()  // secondary hand coords per frame
    private val WINDOW_SIZE = 8
    private var lastSpokenWord = ""
    private var lastSpokenTime = 0L
    private val SPEECH_COOLDOWN = 3000L

    private var isMalay = true
    private var isSpeechOn = false
    private var currentLabelBM = "READY"
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    private var consecutiveLabel = ""
    private var consecutiveCount = 0
    private val REQUIRED_CONSECUTIVE = 5

    private var lastWristX = 0f
    private var lastWristY = 0f
    private val VELOCITY_THRESHOLD = 0.15f

    private var lastDetectionTime = 0L
    private val DETECTION_COOLDOWN = 1500L

    private var geminiHoverStart = 0L
    private var deleteHoverStart = 0L
    private val HOVER_DURATION = 1500L
    private var isGeminiButtonActive = true

    private var confidenceThreshold = 0.95f
    private var lastTheme: Boolean? = null

    private val wordBuffer = mutableListOf<String>()
    private var lastDetectedWord = ""
    private var handMissingCounter = 0
    private val HAND_MISSING_THRESHOLD = 90
    private var isGeneratingSentence = false
    private var hasCalledOnHandGone = false

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime = 0L
    private val SHAKE_THRESHOLD = 800f
    private val SHAKE_COOLDOWN = 1000L

    private lateinit var gestureDetector: GestureDetector

    private val clearBufferHandler = Handler(Looper.getMainLooper())
    private val clearConfidenceHandler = Handler(Looper.getMainLooper())
    private val sentenceDisplayHandler = Handler(Looper.getMainLooper())
    private var sentenceDisplayRunnable: Runnable? = null

    private val clearBufferRunnable = Runnable {
        bufferText.text = ""
        Log.d("CLEAR", "Buffer text cleared")
    }

    private val clearConfidenceRunnable = Runnable {
        confidenceText.text = ""
        confidenceText.visibility = android.view.View.GONE
        Log.d("CLEAR", "Confidence text cleared")
    }

    private var currentSpellingWord = StringBuilder()
    private var lastLetterTime = 0L
    private val LETTER_TIMEOUT = 300L
    private val letterCommitHandler = Handler(Looper.getMainLooper())
    private var letterCommitRunnable: Runnable? = null

    private val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gestureMap = HashMap<String, String>()
    private lateinit var cameraExecutor: ExecutorService
    private val tfliteExecutor = Executors.newSingleThreadExecutor()

    //  Word categories for advanced SVO
    private val kataSalam = setOf("HAI", "MAAF", "TERIMA KASIH")
    private val kataGanti = setOf("SAYA", "AWAK", "KAMU", "KITA", "ABANG", "AYAH", "EMAK")
    private val kataKerja = setOf(
        "AMBIL", "BACA", "BELAJAR", "BELI", "BERI", "KERJA", "LIHAT", "LUKIS",
        "MAIN", "MAKAN", "MANDI", "MINUM", "PERGI", "POTONG", "PUKUL", "TIDUR"
    )
    private val kataTempat = setOf("DOBI", "HOSPITAL", "HOTEL", "KEDAI", "PEJABAT", "RUMAH", "SEKOLAH")
    private val kataBenda = setOf(
        "AIR", "ANJING", "ARNAB", "BUKU", "FERI", "KATIL", "KUCING", "LEMBU",
        "MAJALAH", "MILO", "MOTOSIKAL", "NASI", "ROTI", "TEH", "TELUR"
    )
    private val kataTanya = setOf("BAGAIMANA", "BERAPA", "BILA", "KENAPA", "MANA", "SIAPA")
    private val kataHubung = setOf("DAN", "DENGAN", "INI", "KE")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translator)

        loadGesturesFromJson()

        handOverlay = findViewById(R.id.handOverlay)
        gestureText = findViewById(R.id.gestureText)
        bufferText = findViewById(R.id.bufferText)
        sentenceText = findViewById(R.id.sentenceText)
        confidenceText = findViewById(R.id.confidenceText)

        val sharedPref = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        confidenceThreshold = sharedPref.getInt("sensitivity", 95) / 100f
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnPlaySpeech = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnPlaySpeech)
        val btnLanguage = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnLanguage)
        val btnSettingGear = findViewById<ImageView>(R.id.btnSettingGear)
        val btnSwitchCamera = findViewById<ImageButton>(R.id.btnSwitchCamera)

        classifier = TFLiteClassifier(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        tts = TextToSpeech(this, this)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                clearAllBuffer()
                return true
            }
        })

        findViewById<PreviewView>(R.id.previewView).setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            v.performClick()
            true
        }

        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        btnSettingGear.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            startCamera()
        }

        btnPlaySpeech.setOnClickListener {
            isSpeechOn = !isSpeechOn
            btnPlaySpeech.text = if (isSpeechOn) "Speech: ON" else "Play Speech"
            btnPlaySpeech.setTextColor(if (isSpeechOn) Color.GREEN else if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
            if (!isSpeechOn) {
                tts?.stop()
                lastSpokenWord = ""
            }
        }

        btnLanguage.setOnClickListener {
            isMalay = !isMalay
            btnLanguage.text = if (isMalay) "Language: BM" else "Language: BI"
            if (currentLabelBM != "READY") {
                val key = currentLabelBM.trim().uppercase()
                val displayText = if (isMalay) key.replace("_", " ") else gestureMap[key] ?: key.replace("_", " ")
                bufferText.text = displayText
            }
        }

        applyTheme()
        lastTheme = ThemeManager.isDark(this)
        setupHandLandmarker()
        startCamera()

        // Show tutorial first time only
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("hasSeenTranslatorTutorial", false)) {
            window.decorView.post { startTutorial() }
        }
    }

    private fun startTutorial() {
        val dp = resources.displayMetrics.density

        fun getRect(viewId: Int): android.graphics.RectF {
            val v = findViewById<android.view.View>(viewId) ?: return android.graphics.RectF()
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val padding = (8 * dp)
            return android.graphics.RectF(
                loc[0] - padding, loc[1] - padding,
                loc[0] + v.width + padding, loc[1] + v.height + padding
            )
        }

        val steps = listOf(
            TutorialStep(
                targetRect = getRect(R.id.cameraBox),
                tooltipText = "Sign in front of your camera to detect BIM gestures",
                tooltipPosition = TooltipPosition.BELOW
            ),
            TutorialStep(
                targetRect = run {
                    val chips = findViewById<android.view.View>(R.id.wordBufferChips)
                    val card = chips?.parent?.parent as? android.view.View ?: chips
                    val loc = IntArray(2)
                    card?.getLocationOnScreen(loc)
                    val p = (8 * dp)
                    android.graphics.RectF(
                        (loc[0] - p), (loc[1] - p),
                        (loc[0] + (card?.width ?: 0) + p),
                        (loc[1] + (card?.height ?: 0) + p)
                    )
                },
                tooltipText = "Detected words appear here. Long press any word to remove it",
                tooltipPosition = TooltipPosition.ABOVE
            ),
            TutorialStep(
                targetRect = getRect(R.id.btnHoverGemini),
                tooltipText = "Hover here for 1.5s to send words to Gemini AI for smart sentence construction",
                tooltipPosition = TooltipPosition.BELOW
            ),
            TutorialStep(
                targetRect = getRect(R.id.btnHoverDelete),
                tooltipText = "Hover here for 1.5s to delete the last word",
                tooltipPosition = TooltipPosition.BELOW
            ),
            TutorialStep(
                targetRect = getRect(R.id.layoutStatus),
                tooltipText = "⚡ Offline Mode uses basic SVO structure.\n✦ Gemini Mode constructs natural sentences using AI",
                tooltipPosition = TooltipPosition.BELOW
            )
        )

        TutorialManager(this, steps) {
            getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                .edit().putBoolean("hasSeenTranslatorTutorial", true).apply()
        }.start()
    }

    private fun loadGesturesFromJson() {
        try {
            assets.open("gestures.json").bufferedReader().use { it.readText() }.let { json ->
                val jsonArray = JSONArray(json)
                gestureMap.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val wordBM = obj.getString("word").trim().uppercase()
                    gestureMap[wordBM] = obj.getString("word_en").trim()
                }
            }
        } catch (e: Exception) { Log.e("JSON_ERROR", "${e.message}") }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val sharedPref = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            tts?.setSpeechRate(sharedPref.getInt("speed", 100) / 100f)
        }
    }

    private fun updateGestureDisplay(label: String) {
        currentLabelBM = label
        if (label == "READY") {
            gestureText.text = "READY"
            gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
            return
        }
        val key = label.trim().uppercase()
        val finalText = if (isMalay) key.replace("_", " ") else gestureMap[key] ?: key.replace("_", " ")

        runOnUiThread {
            bufferText.text = finalText
            clearBufferHandler.removeCallbacks(clearBufferRunnable)
            clearBufferHandler.postDelayed(clearBufferRunnable, 3000)
        }

        val isSingleLetter = finalText.length == 1 && finalText[0].isLetter()

        val now = System.currentTimeMillis()
        if (isSpeechOn) {
            if (isSingleLetter) {
                speakOutput(finalText)
            } else {
                if (finalText != lastSpokenWord || (now - lastSpokenTime) > SPEECH_COOLDOWN) {
                    speakOutput(finalText)
                    lastSpokenWord = finalText
                    lastSpokenTime = now
                }
            }
        }

        if (isSingleLetter) {
            val now = System.currentTimeMillis()
            val timeSinceLastLetter = now - lastLetterTime
            val isDifferentLetter = finalText != lastDetectedWord
            val hasGapEnough = timeSinceLastLetter > 1500L

            if (isDifferentLetter || hasGapEnough) {
                lastDetectedWord = finalText
                lastLetterTime = now
                currentSpellingWord.append(finalText)

                letterCommitRunnable?.let { letterCommitHandler.removeCallbacks(it) }
                letterCommitRunnable = Runnable {
                    val timeSinceLast = System.currentTimeMillis() - lastLetterTime
                    if (timeSinceLast >= LETTER_TIMEOUT - 100 && currentSpellingWord.isNotEmpty()) {
                        val spelled = currentSpellingWord.toString()
                        currentSpellingWord.clear()
                        wordBuffer.add(spelled)
                        updateBufferDisplay()
                    }
                }
                letterCommitHandler.postDelayed(letterCommitRunnable!!, LETTER_TIMEOUT)
            }
        } else if (finalText != lastDetectedWord) {
            lastDetectedWord = finalText
            if (currentSpellingWord.isNotEmpty()) {
                wordBuffer.add(currentSpellingWord.toString())
                currentSpellingWord.clear()
                letterCommitRunnable?.let { letterCommitHandler.removeCallbacks(it) }
            }
            wordBuffer.add(finalText)
            updateBufferDisplay()
        }
    }

    private fun scheduleSentenceClearCancel() {
        sentenceDisplayRunnable?.let { sentenceDisplayHandler.removeCallbacks(it) }
        isGeneratingSentence = false
        handMissingCounter = 0
    }

    private fun scheduleSentenceClear() {
        sentenceDisplayRunnable?.let { sentenceDisplayHandler.removeCallbacks(it) }
        sentenceDisplayRunnable = Runnable {
            wordBuffer.clear()
            lastDetectedWord = ""
            lastSpokenWord = ""
            sentenceText.text = ""
            isGeneratingSentence = false
            handMissingCounter = 0
            gestureText.text = "READY"
            gestureText.setTextColor(if (ThemeManager.isDark(this@TranslatorActivity)) Color.WHITE else Color.BLACK)
            findViewById<LinearLayout>(R.id.wordBufferChips).removeAllViews()
        }
        sentenceDisplayHandler.postDelayed(sentenceDisplayRunnable!!, 10000)
    }

    private fun makeChipBg(color: String): android.graphics.drawable.GradientDrawable {
        val bg = android.graphics.drawable.GradientDrawable()
        bg.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        bg.cornerRadius = 32f
        bg.setColor(android.graphics.Color.parseColor(color))
        return bg
    }

    private fun updateBufferDisplay() {
        runOnUiThread {
            val container = findViewById<LinearLayout>(R.id.wordBufferChips)
            container.removeAllViews()
            val dp = resources.displayMetrics.density
            wordBuffer.forEachIndexed { index, word ->
                val chip = TextView(this@TranslatorActivity)
                chip.text = word
                chip.textSize = 14f
                chip.setTextColor(android.graphics.Color.WHITE)
                chip.setTypeface(null, Typeface.BOLD)
                chip.includeFontPadding = false
                chip.gravity = android.view.Gravity.CENTER
                chip.background = makeChipBg("#10B981")
                chip.setPadding((12*dp).toInt(), (4*dp).toInt(), (12*dp).toInt(), (4*dp).toInt())

                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
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
                    val scaleX = ObjectAnimator.ofFloat(chip, "scaleX", 1f, 1.5f, 0f)
                    val scaleY = ObjectAnimator.ofFloat(chip, "scaleY", 1f, 1.5f, 0f)
                    val alpha = ObjectAnimator.ofFloat(chip, "alpha", 1f, 1f, 0f)
                    scaleX.duration = 300
                    scaleY.duration = 300
                    alpha.duration = 300
                    val set = AnimatorSet()
                    set.playTogether(scaleX, scaleY, alpha)
                    set.start()
                    set.addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            if (index < wordBuffer.size) {
                                val removed = wordBuffer.removeAt(index)
                                lastDetectedWord = ""
                                bufferText.text = ""
                                clearBufferHandler.removeCallbacks(clearBufferRunnable)
                                updateBufferDisplay()
                                gestureText.text = "Removed: $removed"
                                gestureText.setTextColor(if (ThemeManager.isDark(this@TranslatorActivity)) Color.WHITE else Color.BLACK)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    gestureText.text = "READY"
                                    gestureText.setTextColor(if (ThemeManager.isDark(this@TranslatorActivity)) Color.WHITE else Color.BLACK)
                                }, 1500)
                            }
                        }
                    })
                    true
                }
                container.addView(chip)
            }

            val scrollView = container.parent as? HorizontalScrollView
            scrollView?.post { scrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT) }
        }
    }

    private var lastGeminiCallTime = 0L
    private val GEMINI_COOLDOWN = 10000L


    private fun arrangeSVO(words: List<String>): String {
        val salam = mutableListOf<String>()
        val tanya = mutableListOf<String>()
        val subjects = mutableListOf<String>()
        val verbs = mutableListOf<String>()
        val benda = mutableListOf<String>()
        val tempat = mutableListOf<String>()
        val kenderaan = mutableListOf<String>()
        val dan = mutableListOf<String>()
        val ke = mutableListOf<String>()
        val lain = mutableListOf<String>()

        // Deduplicate consecutive same words
        val deduped = mutableListOf<String>()
        for (word in words) {
            if (deduped.isEmpty() || deduped.last().uppercase() != word.uppercase()) {
                deduped.add(word)
            }
        }

        for (word in deduped) {
            val w = word.uppercase()
            when {
                w in kataSalam -> salam.add(word)
                w in kataTanya -> tanya.add(word)
                w in kataGanti -> subjects.add(word)
                w in kataKerja -> verbs.add(word)
                w in kataBenda -> benda.add(word)
                w in kataTempat -> tempat.add(word)
                w == "FERI" || w == "MOTOSIKAL" -> kenderaan.add(word)
                w == "DAN" || w == "DENGAN" -> dan.add(word)
                w == "KE" -> ke.add(word)
                w.length == 1 && w[0].isLetter() -> lain.add(word)
                else -> lain.add(word)
            }
        }

        fun insertDan(list: MutableList<String>, conjunctions: MutableList<String>): List<String> {
            if (list.size < 2 || conjunctions.isEmpty()) return list
            val result = mutableListOf<String>()
            for (i in list.indices) {
                result.add(list[i])
                if (i < list.size - 1) result.add(conjunctions[0])
            }
            conjunctions.clear()
            return result
        }

        val result = mutableListOf<String>()

        // 1. Tanya always first
        result.addAll(tanya)

        // 2. Salam
        result.addAll(salam)

        // 3. Subjects — DAN between if 2+
        result.addAll(insertDan(subjects, dan))

        // 4. Verbs
        result.addAll(verbs)

        // 5. Benda — DAN between if 2+
        result.addAll(insertDan(benda, dan))

        // 6. KE + Tempat — DAN between tempat if 2+
        if (ke.isNotEmpty()) result.addAll(ke)
        result.addAll(insertDan(tempat, dan))

        // 8. Kenderaan — DAN between if 2+
        result.addAll(insertDan(kenderaan, dan))

        // 9. Remaining DAN (unused) + lain
        if (dan.isNotEmpty()) result.addAll(dan)
        result.addAll(lain)

        return result.joinToString(" ")
    }

    private fun onHandGone() {
        if (isGeneratingSentence) return

        if (currentSpellingWord.isNotEmpty()) {
            wordBuffer.add(currentSpellingWord.toString())
            currentSpellingWord.clear()
            letterCommitRunnable?.let { letterCommitHandler.removeCallbacks(it) }
            updateBufferDisplay()
        }

        if (wordBuffer.isEmpty()) return
        if (wordBuffer.size < 2) return

        val hasKataKerja = wordBuffer.any { it.uppercase() in kataKerja }
        if (!hasKataKerja) return

        isGeneratingSentence = true
        handMissingCounter = Int.MAX_VALUE

        if (wordBuffer.size <= 3) {
            gestureText.text = "⚡ Offline Mode"
            gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.YELLOW else Color.parseColor("#B8860B"))
            val sentence = arrangeSVO(wordBuffer.toList())
            sentenceText.text = sentence.uppercase()
            sentenceText.setTextColor(if (ThemeManager.isDark(this)) Color.YELLOW else Color.parseColor("#B8860B"))
            if (isSpeechOn) speakOutput(sentence)
            scheduleSentenceClear()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastGeminiCallTime < GEMINI_COOLDOWN) {
                val sentence = arrangeSVO(wordBuffer.toList())
                sentenceText.text = sentence
                sentenceText.setTextColor(if (ThemeManager.isDark(this)) Color.YELLOW else Color.parseColor("#B8860B"))
                if (isSpeechOn) speakOutput(sentence)
                Handler(Looper.getMainLooper()).postDelayed({
                    wordBuffer.clear()
                    lastDetectedWord = ""
                    lastSpokenWord = ""
                    sentenceText.text = ""
                    isGeneratingSentence = false
                    handMissingCounter = 0
                }, 3000)
            } else {
                lastGeminiCallTime = now
                gestureText.text = "✦ Gemini Thinking..."
                gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.GREEN else Color.parseColor("#1a7a4a"))
                sentenceText.text = "Generating sentence..."
                sentenceText.setTextColor(if (ThemeManager.isDark(this)) Color.GREEN else Color.parseColor("#1a7a4a"))
                generateSentenceWithGemini(wordBuffer.toList())
            }
        }
    }

    private fun preprocessForGemini(words: List<String>): String {
        val result = mutableListOf<String>()
        val currentSpell = StringBuilder()
        for (word in words) {
            if (word.length == 1 && word[0].isLetter()) {
                currentSpell.append(word)
            } else {
                if (currentSpell.isNotEmpty()) {
                    result.add("[${currentSpell}?]")
                    currentSpell.clear()
                }
                result.add(word)
            }
        }
        if (currentSpell.isNotEmpty()) result.add("[${currentSpell}?]")
        return result.joinToString(", ")
    }

    private fun generateSentenceWithGemini(words: List<String>) {
        val wordList = preprocessForGemini(words).lowercase()
        val prompt = if (isMalay) {
            "Kau adalah penterjemah Bahasa Isyarat Malaysia (BIM). Susun perkataan-perkataan ini menjadi ayat Bahasa Melayu yang betul dan natural: $wordList. Balas dengan ayat sahaja, tiada penjelasan lain."
        } else {
            "You are a Malaysian Sign Language (BIM) translator. Arrange these words into a proper natural English sentence: $wordList. Reply with the sentence only, no explanation."
        }

        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY")
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    val sentence = arrangeSVO(words)
                    sentenceText.text = sentence.uppercase()
                    sentenceText.setTextColor(if (ThemeManager.isDark(this@TranslatorActivity)) Color.YELLOW else Color.parseColor("#B8860B"))
                    gestureText.text = "⚡ Offline Mode"
                    gestureText.setTextColor(if (ThemeManager.isDark(this@TranslatorActivity)) Color.YELLOW else Color.parseColor("#B8860B"))
                    isGeneratingSentence = false
                    scheduleSentenceClear()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                if (response.code != 200) {
                    runOnUiThread {
                        val sentence = arrangeSVO(words)
                        sentenceText.text = sentence.uppercase()
                        sentenceText.setTextColor(if (ThemeManager.isDark(this@TranslatorActivity)) Color.YELLOW else Color.parseColor("#B8860B"))
                        gestureText.text = "⚡ Offline Mode"
                        gestureText.setTextColor(if (ThemeManager.isDark(this@TranslatorActivity)) Color.YELLOW else Color.parseColor("#B8860B"))
                        isGeneratingSentence = false
                        scheduleSentenceClear()
                    }
                    return
                }
                try {
                    val result = JSONObject(responseBody)
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts").getJSONObject(0)
                        .getString("text").trim()

                    runOnUiThread {
                        sentenceText.text = result.uppercase()
                        sentenceText.setTextColor(if (ThemeManager.isDark(this@TranslatorActivity)) Color.GREEN else Color.parseColor("#1a7a4a"))
                        gestureText.setTextColor(if (ThemeManager.isDark(this@TranslatorActivity)) Color.GREEN else Color.parseColor("#1a7a4a"))
                        if (isSpeechOn) speakOutput(result)
                        scheduleSentenceClear()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        val sentence = arrangeSVO(words)
                        sentenceText.text = sentence.uppercase()
                        sentenceText.setTextColor(if (ThemeManager.isDark(this@TranslatorActivity)) Color.YELLOW else Color.parseColor("#B8860B"))
                        gestureText.text = "⚡ Offline Mode"
                        gestureText.setTextColor(if (ThemeManager.isDark(this@TranslatorActivity)) Color.YELLOW else Color.parseColor("#B8860B"))
                        isGeneratingSentence = false
                        scheduleSentenceClear()
                    }
                }
            }
        })
    }

    private fun speakOutput(text: String) {
        val sharedPref = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, sharedPref.getInt("volume", 80) / 100f)
        }
        tts?.setLanguage(if (isMalay) Locale("ms", "MY") else Locale.US)
        tts?.setSpeechRate(sharedPref.getInt("speed", 100) / 100f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ID_TTS")
    }

    private fun checkHoverButtons(indexTipX: Float, indexTipY: Float) {
        val now = System.currentTimeMillis()
        val inGemini = indexTipX in 0.05f..0.25f && indexTipY in 0.02f..0.40f
        val inDelete = indexTipX in 0.75f..1.00f && indexTipY in 0.00f..0.25f

        if (inGemini) {
            if (geminiHoverStart == 0L) geminiHoverStart = now
            val elapsed = now - geminiHoverStart
            val progress = (elapsed.toFloat() / HOVER_DURATION).coerceIn(0f, 1f)
            runOnUiThread { updateHoverProgress("gemini", progress) }
            if (elapsed >= HOVER_DURATION) {
                geminiHoverStart = 0L
                runOnUiThread { triggerGeminiButton() }
            }
        } else {
            geminiHoverStart = 0L
            runOnUiThread { updateHoverProgress("gemini", 0f) }
        }

        if (inDelete) {
            if (deleteHoverStart == 0L) deleteHoverStart = now
            val elapsed = now - deleteHoverStart
            val progress = (elapsed.toFloat() / HOVER_DURATION).coerceIn(0f, 1f)
            runOnUiThread { updateHoverProgress("delete", progress) }
            if (elapsed >= HOVER_DURATION) {
                deleteHoverStart = 0L
                runOnUiThread { triggerDeleteButton() }
            }
        } else {
            deleteHoverStart = 0L
            runOnUiThread { updateHoverProgress("delete", 0f) }
        }
    }

    private fun updateHoverProgress(button: String, progress: Float) {
        val btnGemini = findViewById<FrameLayout>(R.id.btnHoverGemini)
        val btnDelete = findViewById<FrameLayout>(R.id.btnHoverDelete)
        val btn = if (button == "gemini") btnGemini else btnDelete
        val alpha = if (progress == 0f) 0.7f else 0.7f + (progress * 0.3f)
        btn?.alpha = alpha
        val scale = if (progress == 0f) 1f else 1f + (progress * 0.1f)
        btn?.scaleX = scale
        btn?.scaleY = scale
    }

    private fun triggerGeminiButton() {
        if (wordBuffer.isEmpty()) {
            gestureText.text = "⚠️ No words in buffer"
            gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
            Handler(Looper.getMainLooper()).postDelayed({
                gestureText.text = "READY"
                gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
            }, 2000)
            return
        }
        val isConnected = try {
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1 8.8.8.8")
            process.waitFor() == 0
        } catch (e: Exception) { false }

        if (!isConnected) {
            gestureText.text = "⚠️ No internet connection"
            gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
            isGeneratingSentence = false
            return
        }
        if (isGeneratingSentence) return
        isGeneratingSentence = true
        gestureText.text = "✦ Gemini Thinking..."
        gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.GREEN else Color.parseColor("#1a7a4a"))
        sentenceText.text = "Generating sentence..."
        sentenceText.setTextColor(if (ThemeManager.isDark(this)) Color.GREEN else Color.parseColor("#1a7a4a"))
        generateSentenceWithGemini(wordBuffer.toList())
    }


    private fun triggerDeleteButton() {
        if (wordBuffer.isEmpty()) {
            gestureText.text = "⚠️ Buffer empty"
            gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
            Handler(Looper.getMainLooper()).postDelayed({
                gestureText.text = "READY"
                gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
            }, 2000)
            return
        }
        val removed = wordBuffer.removeLastOrNull()
        updateBufferDisplay()
        gestureText.text = "Removed: $removed"
        gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
        Handler(Looper.getMainLooper()).postDelayed({
            gestureText.text = "READY"
            gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
        }, 1500)
    }

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
            val mag = Math.sqrt((v1x*v1x+v1y*v1y+v1z*v1z).toDouble()) *
                    Math.sqrt((v2x*v2x+v2y*v2y+v2z*v2z).toDouble()) + 1e-8
            Math.acos((dot/mag).coerceIn(-1.0, 1.0)).toFloat()
        }
    }

    private fun extractHandCoords(
        lm: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>
    ): FloatArray {
        val wrist = lm[0]
        val coords = FloatArray(63)
        var idx = 0
        for (p in lm) {
            val normZ = p.z() - wrist.z()
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                coords[idx++] = p.x() - wrist.x()
                coords[idx++] = p.y() - wrist.y()
            } else {
                coords[idx++] = wrist.x() - p.x()
                coords[idx++] = wrist.y() - p.y()
            }
            coords[idx++] = normZ
        }
        return coords
    }

    private fun setupHandLandmarker() {
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .setDelegate(Delegate.GPU)
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setResultListener { result, _ ->
                runOnUiThread {
                    handOverlay.setResults(result, lensFacing == CameraSelector.LENS_FACING_FRONT)
                }

                val landmarksList = result.landmarks()

                if (landmarksList.isNotEmpty()) {
                    handMissingCounter = 0
                    hasCalledOnHandGone = false

                    var rightHandCoords: FloatArray? = null
                    var leftHandCoords: FloatArray? = null
                    val handednessList = result.handednesses()

                    for (i in landmarksList.indices) {
                        val lm = landmarksList[i]
                        val coords = extractHandCoords(lm)
                        val label = if (handednessList != null && i < handednessList.size && handednessList[i].isNotEmpty()) {
                            handednessList[i][0].categoryName()
                        } else {
                            if (i == 0) "Right" else "Left"
                        }
                        val isRight = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            label == "Left"
                        } else {
                            label == "Right"
                        }
                        if (isRight) rightHandCoords = coords else leftHandCoords = coords
                    }

                    val coordsToUse = rightHandCoords ?: leftHandCoords ?: return@setResultListener
                    // Secondary hand: the other hand (if detected), otherwise zeros
                    val secondHand = if (rightHandCoords != null && leftHandCoords != null) {
                        // Both hands detected — secondary is the non-dominant one
                        if (rightHandCoords === coordsToUse) leftHandCoords else rightHandCoords
                    } else {
                        FloatArray(63) // Only one hand detected — secondary is zeros
                    }

                    val rawLm = landmarksList[0]
                    val indexTipX = rawLm[8].x()
                    val indexTipY = rawLm[8].y()
                    val mirroredX = 1f - indexTipX
                    val mirroredY = if (lensFacing == CameraSelector.LENS_FACING_FRONT) 1f - indexTipY else indexTipY
                    val inButtonArea = (mirroredX in 0.05f..0.25f && mirroredY in 0.02f..0.40f) ||
                            (mirroredX in 0.75f..1.00f && mirroredY in 0.00f..0.25f)
                    checkHoverButtons(mirroredX, mirroredY)
                    if (inButtonArea) return@setResultListener

                    frameBuffer.add(coordsToUse)
                    secondHandBuffer.add(secondHand)
                    if (frameBuffer.size > WINDOW_SIZE) {
                        frameBuffer.removeAt(0)
                        secondHandBuffer.removeAt(0)
                    }

                    if (frameBuffer.size == WINDOW_SIZE) {
                        tfliteExecutor.execute {
                            // Dual-hand 262-feature vector:
                            // [SecondHand63] + [DominantStart63] + [DominantStartCurl5]
                            // + [SecondHand63] + [DominantEnd63] + [DominantEndCurl5]
                            val startCurl = computeCurlFeatures(frameBuffer[0])
                            val endCurl = computeCurlFeatures(frameBuffer.last())
                            val secondStart = secondHandBuffer[0]
                            val secondEnd = secondHandBuffer.last()
                            val input = secondStart + frameBuffer[0] + startCurl + secondEnd + frameBuffer.last() + endCurl

                            val (label, confidence) = classifier.predict(input)

                            if (confidence > confidenceThreshold) {
                                if (label == consecutiveLabel) {
                                    consecutiveCount++
                                    if (consecutiveCount >= REQUIRED_CONSECUTIVE) {
                                        val nowDetect = System.currentTimeMillis()
                                        if (nowDetect - lastDetectionTime < DETECTION_COOLDOWN) {
                                            consecutiveLabel = ""
                                            consecutiveCount = 0
                                            return@execute
                                        }
                                        lastDetectionTime = nowDetect
                                        runOnUiThread {
                                            confidenceText.text = String.format("%.1f%%", confidence * 100)
                                            confidenceText.visibility = android.view.View.VISIBLE
                                            confidenceText.setTextColor(when {
                                                confidence >= 0.7f -> Color.GREEN
                                                confidence >= 0.5f -> if (ThemeManager.isDark(this)) Color.YELLOW else Color.parseColor("#B8860B")
                                                else -> Color.RED
                                            })
                                            clearConfidenceHandler.removeCallbacks(clearConfidenceRunnable)
                                            clearConfidenceHandler.postDelayed(clearConfidenceRunnable, 3000)
                                            updateGestureDisplay(label.uppercase())
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
                } else {
                    frameBuffer.clear()
                    secondHandBuffer.clear()
                    consecutiveLabel = ""
                    consecutiveCount = 0
                    lastWristX = 0f
                    lastWristY = 0f
                    lastDetectionTime = 0L
                    handMissingCounter++
                    if (handMissingCounter >= HAND_MISSING_THRESHOLD && !hasCalledOnHandGone) {
                        hasCalledOnHandGone = true
                        runOnUiThread { onHandGone() }
                    }
                }
            }.build()
        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val previewView = findViewById<PreviewView>(R.id.previewView)

            val preview = Preview.Builder()
                .setTargetResolution(Size(480, 640))
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(240, 320))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analyzer.setAnalyzer(cameraExecutor) { image ->
                val bitmap = image.toBitmap()
                handLandmarker.detectAsync(BitmapImageBuilder(bitmap).build(), SystemClock.uptimeMillis())
                image.close()
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    preview,
                    analyzer
                )
            } catch (e: Exception) { Log.e("CAMERA_ERROR", "${e.message}") }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        // Reload all settings without recreating activity (avoids camera restart)
        val sharedPref = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        confidenceThreshold = sharedPref.getInt("sensitivity", 95) / 100f
        tts?.setSpeechRate(sharedPref.getInt("speed", 100) / 100f)
        applyTheme()
        // Restore word buffer chips after returning from settings
        if (wordBuffer.isNotEmpty()) {
            updateBufferDisplay()
        }
    }

    private fun applyTheme() {
        val root = findViewById<ConstraintLayout>(R.id.rootTranslator)
        ThemeManager.apply(activity = this, root = root)
        applyCustomTheme()
    }

    private fun applyCustomTheme() {}

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val force = x * x + y * y + z * z
        if (force > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > SHAKE_COOLDOWN) {
                lastShakeTime = now
                runOnUiThread { undoLastWord() }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun undoLastWord() {
        if (isGeneratingSentence) return
        if (currentSpellingWord.isNotEmpty()) {
            currentSpellingWord.clear()
            letterCommitRunnable?.let { letterCommitHandler.removeCallbacks(it) }
            gestureText.text = "Cleared Spelling"
            gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
        } else if (wordBuffer.isNotEmpty()) {
            val removed = wordBuffer.removeAt(wordBuffer.size - 1)
            gestureText.text = "Removed: $removed"
            gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
        }
        lastDetectedWord = ""
        updateBufferDisplay()
        Handler(Looper.getMainLooper()).postDelayed({
            gestureText.text = "READY"
            gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
        }, 1500)
    }

    private fun clearAllBuffer() {
        sentenceDisplayRunnable?.let { sentenceDisplayHandler.removeCallbacks(it) }
        isGeneratingSentence = false
        handMissingCounter = 0
        wordBuffer.clear()
        currentSpellingWord.clear()
        letterCommitRunnable?.let { letterCommitHandler.removeCallbacks(it) }
        lastDetectedWord = ""
        lastSpokenWord = ""
        sentenceText.text = ""
        bufferText.text = ""
        confidenceText.text = ""
        confidenceText.visibility = android.view.View.GONE
        clearBufferHandler.removeCallbacks(clearBufferRunnable)
        clearConfidenceHandler.removeCallbacks(clearConfidenceRunnable)
        findViewById<LinearLayout>(R.id.wordBufferChips).removeAllViews()
        gestureText.text = "Buffer Cleared"
        gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
        Handler(Looper.getMainLooper()).postDelayed({
            gestureText.text = "READY"
            gestureText.setTextColor(if (ThemeManager.isDark(this)) Color.WHITE else Color.BLACK)
        }, 1500)
    }

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown()
        cameraExecutor.shutdown(); tfliteExecutor.shutdown()
        handLandmarker.close(); classifier.close()
        letterCommitHandler.removeCallbacksAndMessages(null)
        clearBufferHandler.removeCallbacksAndMessages(null)
        clearConfidenceHandler.removeCallbacksAndMessages(null)
        sentenceDisplayHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}