package com.fyp.smartsigntranslator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.max

class HandOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var results: HandLandmarkerResult? = null
    private var isFrontCamera: Boolean = true

    private val skeletonPaint = Paint().apply {
        color = Color.parseColor("#ffffff")
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val jointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val skeletonConnections = listOf(
        Pair(0, 1), Pair(0, 5), Pair(0, 9), Pair(0, 13), Pair(0, 17),
        Pair(1, 5), Pair(5, 9), Pair(9, 13), Pair(13, 17),
        Pair(1, 2), Pair(2, 3), Pair(3, 4),
        Pair(5, 6), Pair(6, 7), Pair(7, 8),
        Pair(9, 10), Pair(10, 11), Pair(11, 12),
        Pair(13, 14), Pair(14, 15), Pair(15, 16),
        Pair(17, 18), Pair(18, 19), Pair(19, 20)
    )

    private val camWidth = 480f
    private val camHeight = 640f

    fun setResults(handResults: HandLandmarkerResult?, isFront: Boolean = true) {
        results = handResults
        isFrontCamera = isFront
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val result = results ?: return
        if (result.landmarks().isEmpty()) return

        val scaleX = width.toFloat() / camWidth
        val scaleY = height.toFloat() / camHeight
        val scale = max(scaleX, scaleY)

        val offsetX = (width - camWidth * scale) / 2f
        val offsetY = (height - camHeight * scale) / 2f

        for (handIndex in 0 until result.landmarks().size) {
            val hand = result.landmarks()[handIndex]

            val positions = Array(21) { Pair(0f, 0f) }

            for (i in 0 until 21) {
                val landmark = hand[i]

                val px: Float
                val py: Float

                if (isFrontCamera) {
                    px = (1 - landmark.y()) * camWidth * scale + offsetX
                    py = (1 - landmark.x()) * camHeight * scale + offsetY
                } else {
                    px = (1 - landmark.y()) * camWidth * scale + offsetX
                    py = landmark.x() * camHeight * scale + offsetY
                }

                positions[i] = Pair(px, py)
            }

            for ((startIdx, endIdx) in skeletonConnections) {
                val (startX, startY) = positions[startIdx]
                val (endX, endY) = positions[endIdx]
                canvas.drawLine(startX, startY, endX, endY, skeletonPaint)
            }

            for (i in 0 until 21) {
                val (px, py) = positions[i]
                val radius = when (i) {
                    0 -> 10f
                    4, 8, 12, 16, 20 -> 9f
                    else -> 6f
                }
                canvas.drawCircle(px, py, radius, jointPaint)
            }
        }
    }
}