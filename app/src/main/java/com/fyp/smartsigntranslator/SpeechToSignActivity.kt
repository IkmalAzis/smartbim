package com.fyp.smartsigntranslator

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.SeekBar
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import org.json.JSONArray
import com.bumptech.glide.Glide
import java.io.IOException

class SpeechToSignActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var gifPlaceholder: ImageView
    private var exoPlayer: ExoPlayer? = null
    private val seekHandler = Handler(android.os.Looper.getMainLooper())
    private lateinit var seekRunnable: Runnable
    private lateinit var tvSpokenText: TextView
    private lateinit var tvFingerspellStatus: TextView
    private lateinit var etTextInput: EditText
    private lateinit var layoutTextInput: LinearLayout

    private val REQUEST_CODE_SPEECH = 100
    private var isMalay = true
    private val bmToUrl = mutableMapOf<String, String>()
    private val enToUrl = mutableMapOf<String, String>()
    private val videoQueue = mutableListOf<String>()
    private var isPlayingQueue = false
    private var lastTheme: Boolean? = null
    private val videoHistory = mutableListOf<String>()
    private var historyIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_speech_to_sign)

        playerView = findViewById(R.id.exo_player_view)
        gifPlaceholder = findViewById(R.id.gifPlaceholder)
        Glide.with(this).asGif().load(R.raw.sign).into(gifPlaceholder)
        tvSpokenText = findViewById(R.id.tvSpokenText)
        tvFingerspellStatus = findViewById(R.id.tvFingerspellStatus)
        etTextInput = findViewById(R.id.etTextInput)
        layoutTextInput = findViewById(R.id.layoutTextInput)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSpeak = findViewById<ImageButton>(R.id.btnSpeak)
        val btnSettingGear = findViewById<ImageView>(R.id.btnSettingGear)
        val btnSubmitText = findViewById<Button>(R.id.btnSubmitText)
        val btnKeyboard = findViewById<AppCompatButton>(R.id.btnKeyboard)
        val btnLanguage = findViewById<AppCompatButton>(R.id.btnLanguage)

        VideoCache.autoDeleteIfNeeded(this)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(VideoCache.getCache(this))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                com.google.android.exoplayer2.source.DefaultMediaSourceFactory(cacheDataSourceFactory)
            )
            .build()
        playerView.player = exoPlayer

        val btnPlayPause = findViewById<ImageButton>(R.id.btnPlayPause)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = findViewById<ImageButton>(R.id.btnNext)
        val btnRewind = findViewById<ImageButton>(R.id.btnRewind)
        val btnFastForward = findViewById<ImageButton>(R.id.btnFastForward)
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        val tvCurrentTime = findViewById<TextView>(R.id.tvCurrentTime)
        val tvTotalTime = findViewById<TextView>(R.id.tvTotalTime)

        btnPlayPause.setOnClickListener {
            if (exoPlayer?.isPlaying == true) {
                exoPlayer?.pause()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                exoPlayer?.play()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        btnRewind.setOnClickListener {
            exoPlayer?.seekTo(maxOf(0, (exoPlayer?.currentPosition ?: 0) - 3000))
        }
        btnFastForward.setOnClickListener {
            exoPlayer?.seekTo((exoPlayer?.currentPosition ?: 0) + 3000)
        }
        btnPrev.setOnClickListener { exoPlayer?.seekTo(0) }
        btnNext.setOnClickListener { exoPlayer?.seekToNext() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) exoPlayer?.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekRunnable = Runnable {
            val duration = exoPlayer?.duration ?: 0
            val position = exoPlayer?.currentPosition ?: 0
            if (duration > 0) {
                seekBar.max = duration.toInt()
                seekBar.progress = position.toInt()
                tvCurrentTime.text = String.format("%02d:%02d", position/60000, (position%60000)/1000)
                tvTotalTime.text = String.format("%02d:%02d", duration/60000, (duration%60000)/1000)
            }
            seekHandler.postDelayed(seekRunnable, 500)
        }
        seekHandler.post(seekRunnable)

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) playNextInQueue()
            }
        })

        val videoContainer = findViewById<android.widget.FrameLayout>(R.id.videoContainer)
        var swipeStartX = 0f
        videoContainer.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> { swipeStartX = event.x; true }
                android.view.MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - swipeStartX
                    if (Math.abs(deltaX) > 100) {
                        if (deltaX > 0) swipeToPrev() else swipeToNext()
                    }
                    true
                }
                else -> false
            }
        }

        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        btnSettingGear.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        btnSpeak.setOnClickListener { startSpeechToText() }

        btnKeyboard.setOnClickListener {
            layoutTextInput.visibility = if (layoutTextInput.visibility == View.VISIBLE)
                View.GONE else View.VISIBLE
        }

        btnSubmitText.setOnClickListener {
            val text = etTextInput.text.toString().trim()
            if (text.isNotEmpty()) {
                tvSpokenText.text = " \' $text\'"
                processInput(text)
                etTextInput.text.clear()
            }
        }

        btnLanguage.setOnClickListener {
            isMalay = !isMalay
            if (isMalay) {
                btnLanguage.text = "Language: BM"
                tvSpokenText.text = "Press mic and speak.."
                etTextInput.hint = "Taip dalam Bahasa Melayu..."
            } else {
                btnLanguage.text = "Language: EN"
                tvSpokenText.text = "Press mic and speak in English..."
                etTextInput.hint = "Type in English..."
            }
        }

        loadGesturesFromJson()
        applyTheme()
        applyCustomTheme()
        lastTheme = ThemeManager.isDark(this)

        // Show tutorial first time only
        val prefs = getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("hasSeenSpeechTutorial", false)) {
            window.decorView.post { startTutorial() }
        }
    }

    private fun startTutorial() {
        val dp = resources.displayMetrics.density

        fun getRect(viewId: Int): android.graphics.RectF {
            val v = findViewById<android.view.View>(viewId) ?: return android.graphics.RectF()
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val p = (8 * dp)
            return android.graphics.RectF(
                loc[0] - p, loc[1] - p,
                loc[0] + v.width + p, loc[1] + v.height + p
            )
        }

        val steps = listOf(
            TutorialStep(
                targetRect = getRect(R.id.videoContainer),
                tooltipText = "Watch BIM gesture videos here. Swipe left/right to navigate between videos",
                tooltipPosition = TooltipPosition.BELOW
            ),
            TutorialStep(
                targetRect = getRect(R.id.inputCard),
                tooltipText = "Your spoken or typed text appears here",
                tooltipPosition = TooltipPosition.BELOW
            ),
            TutorialStep(
                targetRect = getRect(R.id.btnSpeak),
                tooltipText = "Tap to speak — Convert your speech to BIM gesture videos",
                tooltipPosition = TooltipPosition.ABOVE
            ),
            TutorialStep(
                targetRect = getRect(R.id.btnKeyboard),
                tooltipText = "Prefer typing? Tap here to use keyboard input instead",
                tooltipPosition = TooltipPosition.ABOVE
            ),
            TutorialStep(
                targetRect = getRect(R.id.btnLanguage),
                tooltipText = "Switch between Bahasa Melayu and English input",
                tooltipPosition = TooltipPosition.ABOVE
            )
        )

        TutorialManager(this, steps) {
            getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("hasSeenSpeechTutorial", true).apply()
        }.start()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        applyCustomTheme()
    }

    private fun applyTheme() {
        val root = findViewById<ConstraintLayout>(R.id.rootSpeechToSign)
        ThemeManager.apply(activity = this, root = root)
    }

    private fun applyCustomTheme() {
        val dark = ThemeManager.isDark(this)

        val btnPlayPause = findViewById<ImageButton>(R.id.btnPlayPause)
        btnPlayPause.setColorFilter(
            if (dark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#6B7280")
        )

        val controlsBox = findViewById<android.widget.LinearLayout>(R.id.layoutPlayback)
            ?.getChildAt(0) as? android.widget.LinearLayout
        controlsBox?.let {
            val bg = android.graphics.drawable.GradientDrawable()
            bg.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            bg.setColor(android.graphics.Color.TRANSPARENT)
            bg.setStroke(2, android.graphics.Color.parseColor("#000000"))
            it.background = bg
        }

        val inputCard = findViewById<android.widget.LinearLayout>(R.id.inputCard)
        inputCard?.let {
            val bg = android.graphics.drawable.GradientDrawable()
            bg.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            bg.setColor(if (dark) android.graphics.Color.parseColor("#0D0D0D")
            else android.graphics.Color.parseColor("#F0F0F0"))
            bg.setStroke(2, android.graphics.Color.parseColor("#000000"))
            it.background = bg
        }
    }

    private fun loadGesturesFromJson() {
        try {
            val jsonString = assets.open("gestures.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val wordBm = obj.getString("word").uppercase().trim()
                val wordEn = obj.getString("word_en").uppercase().trim()
                val url = obj.getString("url")
                bmToUrl[wordBm] = url
                enToUrl[wordEn] = url
            }
        } catch (e: IOException) { e.printStackTrace() }
    }

    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isMalay) "ms-MY" else "en-US")
        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH)
        } catch (e: Exception) {
            tvSpokenText.text = "Error: Mic unavailable"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SPEECH && resultCode == Activity.RESULT_OK && data != null) {
            val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = result?.get(0)?.trim() ?: ""
            tvSpokenText.text = "\' $spokenText \'"
            processInput(spokenText)
        }
    }

    private fun processInput(sentence: String) {
        val database = if (isMalay) bmToUrl else enToUrl
        val words = sentence.uppercase().trim().split(" ")
        videoQueue.clear()
        val statusParts = mutableListOf<String>()
        for (word in words) {
            if (word.isBlank()) continue
            val url = database[word]
            if (url != null) {
                videoQueue.add(url)
                statusParts.add(word)
            } else {
                for (char in word) {
                    val letterUrl = database[char.toString()]
                    if (letterUrl != null) videoQueue.add(letterUrl)
                }
                statusParts.add("[${word.map { it }.joinToString("-")}]")
            }
        }
        tvFingerspellStatus.text = statusParts.joinToString(" ")
        tvFingerspellStatus.visibility = View.VISIBLE
        if (videoQueue.isNotEmpty()) {
            isPlayingQueue = false
            videoHistory.clear()
            historyIndex = -1
            playNextInQueue()
        }
    }

    private fun playVideo(url: String) {
        gifPlaceholder.visibility = View.GONE
        exoPlayer?.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        exoPlayer?.prepare()
        exoPlayer?.play()
    }

    private fun playNextInQueue() {
        if (videoQueue.isEmpty()) { isPlayingQueue = false; return }
        isPlayingQueue = true
        val nextUrl = videoQueue.removeAt(0)
        if (historyIndex < videoHistory.size - 1) {
            videoHistory.subList(historyIndex + 1, videoHistory.size).clear()
        }
        videoHistory.add(nextUrl)
        historyIndex = videoHistory.size - 1
        playVideo(nextUrl)
    }

    private fun swipeToPrev() {
        if (historyIndex > 0) { historyIndex--; playVideo(videoHistory[historyIndex]) }
    }

    private fun swipeToNext() {
        if (historyIndex < videoHistory.size - 1) { historyIndex++; playVideo(videoHistory[historyIndex]) }
    }

    override fun onDestroy() {
        super.onDestroy()
        seekHandler.removeCallbacks(seekRunnable)
        exoPlayer?.release()
    }
}