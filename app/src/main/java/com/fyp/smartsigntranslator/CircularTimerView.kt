package com.fyp.smartsigntranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CircularTimerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = 0xFF262626.toInt()
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        color = 0xFF10B981.toInt()
    }

    private val rect = RectF()
    var progress = 1f  // 1.0 = full, 0.0 = empty

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) - 8f
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Background circle
        canvas.drawArc(rect, 0f, 360f, false, bgPaint)

        // Progress arc - start from top (-90 degrees)
        val sweep = 360f * progress
        progressPaint.color = when {
            progress > 0.5f -> 0xFF10B981.toInt()
            progress > 0.25f -> 0xFFF59E0B.toInt()
            else -> 0xFFEF4444.toInt()
        }
        canvas.drawArc(rect, -90f, sweep, false, progressPaint)
    }

    fun updateProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        invalidate()
    }
}