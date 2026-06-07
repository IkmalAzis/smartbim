package com.fyp.smartsigntranslator

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.AppCompatButton

data class TutorialStep(
    val targetRect: RectF,
    val tooltipText: String,
    val tooltipPosition: TooltipPosition = TooltipPosition.BELOW
)

enum class TooltipPosition { ABOVE, BELOW, LEFT, RIGHT }

class TutorialOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#CC000000")
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#10B981")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    var spotlightRect: RectF = RectF()
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val sc = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        canvas.drawRoundRect(spotlightRect, 16f, 16f, clearPaint)

        canvas.restoreToCount(sc)

        canvas.drawRoundRect(spotlightRect, 16f, 16f, borderPaint)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
}

class TutorialManager(
    private val activity: android.app.Activity,
    private val steps: List<TutorialStep>,
    private val onFinished: () -> Unit
) {
    private var currentStep = 0
    private lateinit var overlay: TutorialOverlayView
    private lateinit var tooltipCard: LinearLayout
    private lateinit var tvTooltip: TextView
    private lateinit var btnNext: AppCompatButton
    private lateinit var tvStepCount: TextView
    private val rootView = activity.window.decorView as ViewGroup

    fun start() {
        // Overlay
        overlay = TutorialOverlayView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Tooltip card
        tooltipCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val bg = android.graphics.drawable.GradientDrawable()
            bg.cornerRadius = 24f
            bg.setColor(Color.parseColor("#1A1A1A"))
            bg.setStroke(2, Color.parseColor("#10B981"))
            background = bg
            setPadding(40, 28, 40, 28)
            elevation = 16f
        }

        val dp = activity.resources.displayMetrics.density

        tvStepCount = TextView(activity).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#10B981"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.1f
        }

        tvTooltip = TextView(activity).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            (activity.resources.displayMetrics.density).also { d ->
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = (6 * d).toInt()
                lp.bottomMargin = (12 * d).toInt()
                layoutParams = lp
            }
            setLineSpacing(0f, 1.4f)
        }

        btnNext = AppCompatButton(activity).apply {
            text = "Next →"
            textSize = 13f
            setTextColor(Color.WHITE)
            isAllCaps = false
            val bg = android.graphics.drawable.GradientDrawable()
            bg.cornerRadius = 20f
            bg.setColor(Color.parseColor("#10B981"))
            background = bg
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (36 * dp).toInt()
            )
            lp.gravity = android.view.Gravity.END
            layoutParams = lp
            setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), 0)
            setOnClickListener { nextStep() }
        }

        tooltipCard.addView(tvStepCount)
        tooltipCard.addView(tvTooltip)
        tooltipCard.addView(btnNext)

        rootView.addView(overlay)
        rootView.addView(tooltipCard)

        overlay.setOnClickListener { } // consume clicks

        showStep(0)
    }

    private fun showStep(index: Int) {
        if (index >= steps.size) {
            finish()
            return
        }
        currentStep = index
        val step = steps[index]

        // Update spotlight
        overlay.spotlightRect = step.targetRect

        // Update tooltip
        tvStepCount.text = "STEP ${index + 1} OF ${steps.size}"
        tvTooltip.text = step.tooltipText
        btnNext.text = if (index == steps.size - 1) "Got it! ✓" else "Next →"

        // Position tooltip
        val screenWidth = activity.resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = activity.resources.displayMetrics.heightPixels.toFloat()
        val tooltipWidth = (screenWidth * 0.75f).toInt()

        val lp = android.widget.FrameLayout.LayoutParams(
            tooltipWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val margin = (16 * activity.resources.displayMetrics.density).toInt()
        val tooltipHeight = (140 * activity.resources.displayMetrics.density).toInt()

        when (step.tooltipPosition) {
            TooltipPosition.BELOW -> {
                lp.topMargin = (step.targetRect.bottom + margin).toInt()
                lp.leftMargin = margin
            }
            TooltipPosition.ABOVE -> {
                lp.topMargin = (step.targetRect.top - tooltipHeight - margin).toInt().coerceAtLeast(margin)
                lp.leftMargin = margin
            }
            TooltipPosition.LEFT -> {
                lp.topMargin = (step.targetRect.centerY() - tooltipHeight / 2).toInt()
                lp.leftMargin = margin
            }
            TooltipPosition.RIGHT -> {
                lp.topMargin = (step.targetRect.centerY() - tooltipHeight / 2).toInt()
                lp.leftMargin = (step.targetRect.right + margin).toInt()
            }
        }

        tooltipCard.layoutParams = lp

        // Animate in
        tooltipCard.alpha = 0f
        tooltipCard.animate().alpha(1f).setDuration(300).start()
    }

    private fun nextStep() {
        tooltipCard.animate().alpha(0f).setDuration(200).withEndAction {
            showStep(currentStep + 1)
        }.start()
    }

    private fun finish() {
        rootView.removeView(overlay)
        rootView.removeView(tooltipCard)
        onFinished()
    }
}