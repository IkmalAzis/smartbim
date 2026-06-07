package com.fyp.smartsigntranslator

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.*

class SettingsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private lateinit var sbVolume: SeekBar
    private lateinit var sbSpeed: SeekBar
    private lateinit var sbSensitivity: SeekBar
    private lateinit var tvSensitivityValue: TextView
    private lateinit var tvVolumeValue: TextView
    private lateinit var tvSpeedValue: TextView
    private lateinit var btnThemeToggle: AppCompatButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sbVolume = findViewById(R.id.sbVolume)
        sbSpeed = findViewById(R.id.sbSpeed)
        sbSensitivity = findViewById(R.id.sbSensitivity)
        tvSensitivityValue = findViewById(R.id.tvSensitivityValue)
        tvVolumeValue = findViewById(R.id.tvVolumeValue)
        tvSpeedValue = findViewById(R.id.tvSpeedValue)
        btnThemeToggle = findViewById(R.id.btnThemeToggle)
        val btnClose = findViewById<ImageButton>(R.id.btnClose)
        val btnAbout = findViewById<AppCompatButton>(R.id.btnAbout)
        val btnWordsLibrary = findViewById<AppCompatButton>(R.id.btnWordsLibrary)
        val btnClearCache = findViewById<AppCompatButton>(R.id.btnClearCache)
        val tvCacheSize = findViewById<TextView>(R.id.tvCacheSize)

        // Show current cache size
        tvCacheSize.text = VideoCache.getCacheSize(this)

        btnClearCache.setOnClickListener {
            VideoCache.clearCache(this)
            tvCacheSize.text = "0 MB"
            android.widget.Toast.makeText(this, "Cache cleared!", android.widget.Toast.LENGTH_SHORT).show()
        }

        tts = TextToSpeech(this, this, "com.google.android.tts")

        val sharedPref = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        sbVolume.progress = sharedPref.getInt("volume", 80)
        sbSpeed.progress = sharedPref.getInt("speed", 100)
        tvVolumeValue.text = "${sharedPref.getInt("volume", 80)}%"
        tvSpeedValue.text = "${sharedPref.getInt("speed", 100) / 2}%"

        val savedSensitivity = sharedPref.getInt("sensitivity", 95)
        sbSensitivity.progress = savedSensitivity - 50
        tvSensitivityValue.text = "$savedSensitivity%"

        updateThemeBtn()
        applyTheme()

        sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sharedPref.edit().putInt("volume", progress).apply()
                tvVolumeValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) { playTestVoice() }
        })

        sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sharedPref.edit().putInt("speed", progress).apply()
                tvSpeedValue.text = "${progress / 2}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) { playTestVoice() }
        })

        sbSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualValue = progress + 50
                tvSensitivityValue.text = "$actualValue%"
                sharedPref.edit().putInt("sensitivity", actualValue).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnThemeToggle.setOnClickListener {
            ThemeManager.toggle(this)
            recreate()
        }

        btnClose.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        btnWordsLibrary.setOnClickListener {
            startActivity(Intent(this, WordsLibraryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    private fun updateThemeBtn() {
        btnThemeToggle.text = if (ThemeManager.isDark(this)) "Theme: Dark 🌙" else "Theme: Light ☀️"
    }

    private fun applyTheme() {
        val root = findViewById<ConstraintLayout>(R.id.rootSettings)
        ThemeManager.apply(activity = this, root = root)
        updateThemeBtn()
        applyCustomTheme()
    }

    private fun applyCustomTheme() {
        val dark = ThemeManager.isDark(this)

        // Settings card bg
        val settingsCard = findViewById<LinearLayout>(R.id.settingsCard)
        settingsCard?.let {
            val bg = GradientDrawable()
            bg.cornerRadius = 40f
            bg.setColor(if (dark) Color.parseColor("#0D0D0D") else Color.parseColor("#F0F0F0"))
            bg.setStroke(2, Color.BLACK)
            it.background = bg
        }

        // Icon backgrounds
        val iconIds = listOf(R.id.iconVolume, R.id.iconSpeed, R.id.iconSensitivity, R.id.iconDarkMode, R.id.iconCache)
        for (id in iconIds) {
            val iconView = findViewById<ImageView>(id) ?: continue
            val bg = GradientDrawable()
            bg.cornerRadius = 28f
            if (dark) {
                bg.setColor(Color.parseColor("#0A2318"))
                bg.setStroke(0, Color.TRANSPARENT)
            } else {
                bg.setColor(Color.WHITE)
                bg.setStroke(4, Color.parseColor("#10B981"))
            }
            iconView.background = bg
        }

        // Words Library + About button — same style
        val actionButtons = listOf(
            findViewById<AppCompatButton>(R.id.btnWordsLibrary),
            findViewById<AppCompatButton>(R.id.btnAbout)
        )
        for (btn in actionButtons) {
            val bg = GradientDrawable()
            bg.cornerRadius = 28f
            if (dark) {
                bg.setColor(Color.WHITE)
                btn.background = bg
                btn.setTextColor(Color.BLACK)
                androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
                    btn, android.content.res.ColorStateList.valueOf(Color.BLACK)
                )
            } else {
                bg.setColor(Color.BLACK)
                btn.background = bg
                btn.setTextColor(Color.WHITE)
                androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
                    btn, android.content.res.ColorStateList.valueOf(Color.WHITE)
                )
            }
        }

        // Theme toggle
        val toggleBg = GradientDrawable()
        toggleBg.cornerRadius = 20f
        toggleBg.setColor(if (dark) Color.parseColor("#1A1A1A") else Color.parseColor("#E0E0E0"))
        toggleBg.setStroke(1, Color.BLACK)
        btnThemeToggle.background = toggleBg
        btnThemeToggle.setTextColor(if (dark) Color.WHITE else Color.BLACK)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ms", "MY"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
            }
            isTtsReady = true
        }
    }

    private fun playTestVoice() {
        if (!isTtsReady) return
        val sharedPref = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val volume = sharedPref.getInt("volume", 80) / 100f
        val speed = sharedPref.getInt("speed", 100) / 100f
        tts?.setSpeechRate(speed)
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        Handler(Looper.getMainLooper()).postDelayed({
            tts?.speak("Ujian suara", TextToSpeech.QUEUE_FLUSH, params, "TestID")
        }, 100)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}