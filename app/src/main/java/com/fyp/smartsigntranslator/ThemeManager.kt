package com.fyp.smartsigntranslator

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.AppCompatButton
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout

object ThemeManager {

    // ── Dark (black-based) ─────────────────────────────────────
    private val DARK_BG          = Color.parseColor("#000000")
    private val DARK_CARD        = Color.parseColor("#0D0D0D")
    private val DARK_HEADER      = Color.parseColor("#000000")
    private val DARK_TEXT        = Color.parseColor("#FFFFFF")
    private val DARK_SUBTEXT     = Color.parseColor("#71717A")
    private val DARK_BORDER      = Color.parseColor("#262626")
    private val DARK_EDIT_BG     = Color.parseColor("#111111")

    // ── Light (white-based, keep green accent) ─────────────────
    private val LIGHT_BG         = Color.parseColor("#FFFFFF")
    private val LIGHT_CARD       = Color.parseColor("#F4F4F5")
    private val LIGHT_HEADER     = Color.parseColor("#FFFFFF")
    private val LIGHT_TEXT       = Color.parseColor("#000000")
    private val LIGHT_SUBTEXT    = Color.parseColor("#71717A")
    private val LIGHT_BORDER     = Color.parseColor("#E4E4E7")
    private val LIGHT_EDIT_BG    = Color.parseColor("#F4F4F5")

    // Green accent — same for both modes
    private val EMERALD          = Color.parseColor("#0D9268")

    fun isDark(context: Context) =
        context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            .getBoolean("darkTheme", true) // default dark

    fun toggle(context: Context): Boolean {
        val newDark = !isDark(context)
        context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            .edit().putBoolean("darkTheme", newDark).apply()
        return newDark
    }

    fun apply(
        activity: Activity,
        root: ConstraintLayout,
        headerIds: List<Int> = emptyList(),
        yellowBg: Boolean = false,
        keepOrangeTitle: Boolean = false,
        orangeTitleId: Int = -1
    ) {
        val dark = isDark(activity)

        // Root background
        if (!dark) {
            val isMainPage = try {
                root.id == root.resources.getIdentifier("rootMain", "id", root.context.packageName)
            } catch (e: Exception) { false }

            if (isMainPage) {
                root.background = androidx.core.content.ContextCompat.getDrawable(
                    root.context, root.resources.getIdentifier("bg_main_gradient_light", "drawable", root.context.packageName)
                )
            } else {
                root.setBackgroundColor(LIGHT_BG)
            }
        }

        headerIds.forEach { id ->
            root.findViewById<View>(id)?.setBackgroundColor(
                if (dark) DARK_HEADER else LIGHT_HEADER
            )
        }

        walkView(root, dark, keepOrangeTitle, orangeTitleId, root)
    }

    private fun walkView(view: View, dark: Boolean, keepOrangeTitle: Boolean, orangeTitleId: Int, rootView: View? = null) {
        val textColor    = if (dark) DARK_TEXT    else LIGHT_TEXT
        val subtextColor = if (dark) DARK_SUBTEXT else LIGHT_SUBTEXT
        val borderColor  = if (dark) DARK_BORDER  else LIGHT_BORDER

        when {
            view is EditText -> {
                view.setTextColor(textColor)
                view.setHintTextColor(subtextColor)
                view.setBackgroundColor(if (dark) DARK_EDIT_BG else LIGHT_EDIT_BG)
                return
            }

            view is AppCompatButton || view is Button -> {
                val tv = view as TextView
                if (tv.currentTextColor == Color.parseColor("#EF4444") ||
                    tv.currentTextColor == Color.RED) {
                    val d = GradientDrawable().apply {
                        cornerRadius = 28f
                        setColor(if (dark) DARK_CARD else LIGHT_CARD)
                        setStroke(1, borderColor)
                    }
                    view.background = d
                    return
                }
                val d = GradientDrawable().apply {
                    cornerRadius = 28f
                    setColor(Color.TRANSPARENT)
                    setStroke(2, borderColor)
                }
                view.background = d
                tv.setTextColor(textColor)
                return
            }

            view is ImageButton -> {
                val tag = view.tag?.toString()
                if (tag == "mic_btn" && !dark) {
                    val bg = GradientDrawable()
                    bg.shape = GradientDrawable.OVAL
                    bg.setColor(Color.WHITE)
                    bg.setStroke(2, Color.parseColor("#10B981"))
                    view.background = bg
                }
                return
            }

            view is ImageView -> return

            view is TextView -> {
                if (keepOrangeTitle && view.id == orangeTitleId) {
                    view.setTextColor(if (dark) DARK_TEXT else LIGHT_TEXT)
                    return
                }
                try {
                    if (view.resources.getResourceEntryName(view.id) == "confidenceText") return
                } catch (_: Exception) {}

                val isSubtext = view.tag?.toString() == "subtext"
                val currentColor = view.currentTextColor
                val isGrey = currentColor == Color.parseColor("#71717A") ||
                        currentColor == Color.parseColor("#52525B") ||
                        currentColor == Color.parseColor("#3F3F46") ||
                        currentColor == Color.parseColor("#A1A1AA")
                view.setTextColor(if (isSubtext || isGrey) subtextColor else textColor)

                if (view.background is GradientDrawable) {
                    val bg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 12f
                        setColor(if (dark) DARK_CARD else LIGHT_CARD)
                        setStroke(1, borderColor)
                    }
                    view.background = bg
                }
                return
            }

            view is CardView -> {
                val cv = view
                // Check if this is Train Data card (has red child)
                val isRedCard = (0 until cv.childCount).any { i ->
                    val child = cv.getChildAt(i)
                    child is ViewGroup && (0 until child.childCount).any { j ->
                        val inner = child.getChildAt(j)
                        inner is ViewGroup && (0 until inner.childCount).any { k ->
                            val img = inner.getChildAt(k)
                            img is android.widget.ImageView &&
                                    img.imageTintList?.defaultColor == Color.parseColor("#EF4444")
                        }
                    }
                }
                val strokeColor = if (isRedCard) Color.parseColor("#EF4444") else EMERALD
                if (dark) {
                    cv.setCardBackgroundColor(DARK_CARD)
                    val border = GradientDrawable().apply {
                        cornerRadius = cv.radius
                        setColor(Color.TRANSPARENT)
                        setStroke(3, strokeColor)
                    }
                    cv.foreground = border
                } else {
                    cv.setCardBackgroundColor(Color.parseColor("#F0F0F0"))
                    cv.cardElevation = 0f
                    val border = GradientDrawable().apply {
                        cornerRadius = cv.radius
                        setColor(Color.TRANSPARENT)
                        setStroke(3, strokeColor)
                    }
                    cv.foreground = border
                }
                for (i in 0 until cv.childCount) {
                    walkView(cv.getChildAt(i), dark, keepOrangeTitle, orangeTitleId, rootView)
                }
            }

            view is ViewGroup -> {
                val tag = view.tag?.toString()

                if (tag == "skip_theme") return

                if (tag == "icon_bg" && view.background != null) {
                    val childTintRed = (view as? android.view.ViewGroup)?.let { vg ->
                        (0 until vg.childCount).any { i ->
                            val child = vg.getChildAt(i)
                            child is android.widget.ImageView &&
                                    child.imageTintList?.defaultColor == Color.parseColor("#EF4444")
                        }
                    } ?: false

                    val bg = GradientDrawable()
                    bg.cornerRadius = 28f
                    if (dark) {
                        bg.setColor(if (childTintRed) Color.parseColor("#2D0F0F") else Color.parseColor("#0A2318"))
                        bg.setStroke(2, if (childTintRed) Color.parseColor("#EF4444") else EMERALD)
                    } else {
                        bg.setColor(Color.WHITE)
                        bg.setStroke(4, if (childTintRed) Color.parseColor("#EF4444") else EMERALD)
                    }
                    view.background = bg
                }

                if (view !== rootView && tag != "icon_bg" && view.background is GradientDrawable) {
                    val bg = GradientDrawable().apply {
                        cornerRadius = 28f
                        if (dark) {
                            setColor(DARK_CARD)
                            setStroke(1, borderColor)
                        } else {
                            setColor(Color.parseColor("#F0F0F0"))
                            setStroke(2, EMERALD)
                        }
                    }
                    view.background = bg
                }
                for (i in 0 until view.childCount) {
                    walkView(view.getChildAt(i), dark, keepOrangeTitle, orangeTitleId)
                }
            }
        }
    }
}