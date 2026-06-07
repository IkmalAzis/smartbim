package com.fyp.smartsigntranslator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#0D0D0D"))
        setContentView(R.layout.activity_splash)

        val ivLogo = findViewById<ImageView>(R.id.ivSplashLogo)
        // Round logo
        val roundBg = android.graphics.drawable.GradientDrawable()
        roundBg.shape = android.graphics.drawable.GradientDrawable.OVAL
        roundBg.setColor(android.graphics.Color.WHITE)
        ivLogo.background = roundBg
        ivLogo.clipToOutline = true
        ivLogo.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        val tvAppName = findViewById<TextView>(R.id.tvSplashName)
        val tvTagline = findViewById<TextView>(R.id.tvSplashTagline)

        ivLogo.alpha = 0f
        ivLogo.scaleX = 0.7f
        ivLogo.scaleY = 0.7f
        ivLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                ivLogo.animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .setDuration(250)
                    .withEndAction {
                        ivLogo.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(250)
                            .withEndAction {
                                startTypewriter(tvAppName, "Where Hands Speak", 70) {
                                    tvTagline.alpha = 0f
                                    tvTagline.visibility = android.view.View.VISIBLE
                                    tvTagline.animate()
                                        .alpha(1f)
                                        .setDuration(600)
                                        .start()

                                    handler.postDelayed({ navigateNext() }, 2000)
                                }
                            }.start()
                    }.start()
            }.start()
    }

    private fun startTypewriter(tv: TextView, text: String, delay: Long, onDone: () -> Unit) {
        tv.text = ""
        tv.visibility = android.view.View.VISIBLE
        var index = 0
        val runnable = object : Runnable {
            override fun run() {
                if (index <= text.length) {
                    tv.text = text.substring(0, index)
                    index++
                    handler.postDelayed(this, delay)
                } else {
                    onDone()
                }
            }
        }
        handler.post(runnable)
    }

    private fun navigateNext() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA), 101)
        } else {
            goToNext()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        goToNext()
    }

    private fun goToNext() {
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val hasSeenOnboarding = prefs.getBoolean("hasSeenOnboarding", false)
        val intent = if (hasSeenOnboarding) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, OnboardingActivity::class.java)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#0D0D0D"))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}