package com.fyp.smartsigntranslator

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout

class MainActivity : AppCompatActivity() {
    private var lastTheme: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge to edge
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        setContentView(R.layout.activity_main2)

        findViewById<CardView>(R.id.btnTranslator).setOnClickListener {
            animateCardClick(it)
            startActivity(Intent(this, TranslatorActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        findViewById<CardView>(R.id.btnSpeechToSign).setOnClickListener {
            animateCardClick(it)
            startActivity(Intent(this, SpeechToSignActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Train data

//        findViewById<CardView>(R.id.btnTrain).setOnClickListener {
//            animateCardClick(it)
//            startActivity(Intent(this, DataCollectorActivity::class.java))
//            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
//        }


        findViewById<CardView>(R.id.cardQuiz).setOnClickListener {
            animateCardClick(it)
            startActivity(Intent(this, QuizActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        findViewById<android.widget.ImageView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        lastTheme = ThemeManager.isDark(this)
        applyTheme()
        playEntranceAnimations()
    }

    override fun onResume() {
        super.onResume()
        val currentTheme = ThemeManager.isDark(this)
        if (lastTheme != null && lastTheme != currentTheme) {
            recreate()
            overridePendingTransition(0, 0)
            return
        }
        lastTheme = currentTheme
        applyTheme()
    }

    private fun updateCardTexts(view: android.view.View, titleColor: Int, subtextColor: Int) {
        if (view is android.widget.TextView) {
            val tag = view.tag?.toString()
            if (tag == "subtext") view.setTextColor(subtextColor)
            else if (view.currentTextColor == android.graphics.Color.parseColor("#E8F5EC") ||
                view.currentTextColor == android.graphics.Color.parseColor("#F0F0F0") ||
                view.currentTextColor == android.graphics.Color.WHITE) {
                view.setTextColor(titleColor)
            }
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) updateCardTexts(view.getChildAt(i), titleColor, subtextColor)
        }
    }

    private fun applyTheme() {
        val dark = ThemeManager.isDark(this)
        val root = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rootMain)
        ThemeManager.apply(activity = this, root = root)

        // Main page specific overrides
        val cardBg = if (dark) android.graphics.Color.parseColor("#141F17") else android.graphics.Color.parseColor("#F0F9F4")
        val titleColor = if (dark) android.graphics.Color.parseColor("#E8F5EC") else android.graphics.Color.parseColor("#0D1F15")
        val subtitleColor = if (dark) android.graphics.Color.parseColor("#6B9B7A") else android.graphics.Color.parseColor("#4A7A5A")
        val subtextColor = if (dark) android.graphics.Color.parseColor("#6B9B7A") else android.graphics.Color.parseColor("#4A7A5A")
        val eyebrowColor = android.graphics.Color.parseColor("#10B981")

        // Update card backgrounds
        listOf(R.id.btnTranslator, R.id.btnSpeechToSign, R.id.cardQuiz).forEach { id ->
            val card = findViewById<androidx.cardview.widget.CardView>(id) ?: return@forEach
            card.setCardBackgroundColor(cardBg)
        }

        // Update texts
        val tvTitle = findViewById<android.widget.TextView>(R.id.tvTitle)
        tvTitle?.setTextColor(if (dark) android.graphics.Color.parseColor("#E8F5EC") else android.graphics.Color.BLACK)
        if (dark) {
            tvTitle?.setShadowLayer(5f, 0f, 0f, android.graphics.Color.parseColor("#7710B981"))
        } else {
            tvTitle?.setShadowLayer(3f, 2f, 2f, android.graphics.Color.parseColor("#5510B981"))
        }
        // Wordmark color — emerald in light mode
        val tvWordmark = findViewById<android.widget.TextView>(R.id.tvWordmark)
        tvWordmark?.setTextColor(
            if (dark) android.graphics.Color.parseColor("#6B9B7A")
            else android.graphics.Color.BLACK
        )

        // Divider — brighter in light mode
        val divider = findViewById<android.view.View>(R.id.dividerLine)
        divider?.let {
            val grad = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                if (dark) intArrayOf(
                    android.graphics.Color.parseColor("#3310B981"),
                    android.graphics.Color.parseColor("#0010B981")
                ) else intArrayOf(
                    android.graphics.Color.parseColor("#6610B981"),
                    android.graphics.Color.parseColor("#0010B981")
                )
            )
            it.background = grad
        }

        // Settings button bg
        val btnSettings = findViewById<android.widget.ImageView>(R.id.btnSettings)
        btnSettings?.let {
            val bg = android.graphics.drawable.GradientDrawable()
            bg.shape = android.graphics.drawable.GradientDrawable.OVAL
            bg.setColor(if (dark) android.graphics.Color.parseColor("#111C16") else android.graphics.Color.WHITE)
            bg.setStroke(2, android.graphics.Color.parseColor("#0D9268"))
            it.background = bg
        }
        val tvSubtitle = findViewById<android.widget.TextView>(R.id.tvSubtitle)
        tvSubtitle?.setTextColor(subtitleColor)

        // Update card title + subtitle texts
        listOf(R.id.btnTranslator, R.id.btnSpeechToSign, R.id.cardQuiz).forEach { id ->
            val card = findViewById<androidx.cardview.widget.CardView>(id) ?: return@forEach
            updateCardTexts(card, titleColor, subtextColor)
        }

        // Root bg
        if (!dark) {
            root.setBackgroundColor(android.graphics.Color.WHITE)
        } else {
            root.setBackgroundColor(android.graphics.Color.parseColor("#0A0F0C"))
        }
    }

    private fun playEntranceAnimations() {
        val fadeUp = AnimationUtils.loadAnimation(this, R.anim.fade_up)

        val views = listOf(
            findViewById<View>(R.id.btnSettings),
            findViewById<View>(R.id.heroSection),
            findViewById<CardView>(R.id.btnTranslator),
            findViewById<CardView>(R.id.btnSpeechToSign),
            findViewById<CardView>(R.id.cardQuiz)
        )

        views.forEachIndexed { index, view ->
            view.postDelayed({
                view.startAnimation(fadeUp)
                view.visibility = View.VISIBLE
            }, index * 80L)
        }
    }

    private fun animateCardClick(view: View) {
        view.animate()
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }.start()
    }
}