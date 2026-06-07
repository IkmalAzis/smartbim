package com.fyp.smartsigntranslator

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val btnBackAbout = findViewById<ImageButton>(R.id.btnBackAbout)
        btnBackAbout.setOnClickListener { finish() }

        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    private fun applyTheme() {
        val root = findViewById<ConstraintLayout>(R.id.rootAbout)
        val dark = ThemeManager.isDark(this)

        if (dark) {
            root.setBackgroundResource(R.drawable.bg_main_gradient)
        } else {
            root.setBackgroundResource(R.drawable.bg_main_gradient_light)
        }

        val textColor = if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val subTextColor = if (dark) android.graphics.Color.parseColor("#CCCCCC") else android.graphics.Color.parseColor("#333333")

        setTextColorsRecursive(root, textColor, subTextColor)
    }

    private fun setTextColorsRecursive(view: android.view.View, textColor: Int, subTextColor: Int) {
        if (view is android.widget.TextView) {
            view.setTextColor(if (view.textSize > 50f) textColor else subTextColor)
        } else if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                setTextColorsRecursive(view.getChildAt(i), textColor, subTextColor)
            }
        }
    }
}