package com.fyp.smartsigntranslator

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

sealed class SlideType {
    data class Welcome(
        val logoRes: Int,
        val title: String,
        val description: String
    ) : SlideType()

    data class Features(
        val title: String
    ) : SlideType()

    data class DualMockup(
        val topImage: String,
        val topTitle: String,
        val topDesc: String,
        val bottomImage: String,
        val bottomTitle: String,
        val bottomDesc: String
    ) : SlideType()

    data class GetStarted(
        val title: String,
        val description: String
    ) : SlideType()
}

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsLayout: LinearLayout
    private lateinit var btnNext: AppCompatButton
    private lateinit var btnSkip: TextView

    private val slides = listOf(
        SlideType.Welcome(
            logoRes = R.drawable.ic_logo_smartbim,
            title = "Where Hands Speak",
            description = "Master Bahasa Isyarat Malaysia with the power of AI. Real-time gesture detection, interactive practice, and instant translation — all in one app, anytime, anywhere."
        ),
        SlideType.Features(title = "Key Features"),
        SlideType.DualMockup(
            topImage = "translate_sign.jpg",
            topTitle = "Translate Sign",
            topDesc = "Point your camera, sign a gesture, and get instant BIM translation",
            bottomImage = "translate_speech.jpg",
            bottomTitle = "Translate Speech",
            bottomDesc = "Type or speak any word and see the BIM gesture in action"
        ),
        SlideType.DualMockup(
            topImage = "quiz_mode.jpg",
            topTitle = "Quiz Mode",
            topDesc = "Test your BIM knowledge with 3 difficulty levels",
            bottomImage = "game_mode.jpg",
            bottomTitle = "Sign Blitz",
            bottomDesc = "Race against the clock — sign as many words as you can!"
        ),
        SlideType.GetStarted(
            title = "You're All Set!",
            description = "Let's start signing and exploring the world of BIM"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        dotsLayout = findViewById(R.id.dotsLayout)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)

        viewPager.adapter = OnboardingPagerAdapter(slides, assets)
        setupDots(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setupDots(position)
                if (position == slides.size - 1) {
                    btnNext.text = "Get Started 🤟"
                    btnSkip.visibility = View.INVISIBLE
                } else {
                    btnNext.text = "Next →"
                    btnSkip.visibility = View.VISIBLE
                }
            }
        })

        btnNext.setOnClickListener {
            val current = viewPager.currentItem
            if (current < slides.size - 1) {
                viewPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        btnSkip.setOnClickListener { finishOnboarding() }
    }

    private fun setupDots(activeIndex: Int) {
        dotsLayout.removeAllViews()
        val dp = resources.displayMetrics.density
        for (i in slides.indices) {
            val dot = View(this)
            val isActive = i == activeIndex
            val width = if (isActive) (24 * dp).toInt() else (8 * dp).toInt()
            val lp = LinearLayout.LayoutParams(width, (8 * dp).toInt())
            lp.marginEnd = (6 * dp).toInt()
            dot.layoutParams = lp
            val bg = GradientDrawable()
            bg.cornerRadius = 16f
            bg.setColor(if (isActive) Color.parseColor("#10B981") else Color.parseColor("#3A3A3A"))
            dot.background = bg
            dotsLayout.addView(dot)
        }
    }

    private fun finishOnboarding() {
        getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            .edit().putBoolean("hasSeenOnboarding", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }
}

class OnboardingPagerAdapter(
    private val slides: List<SlideType>,
    private val assets: android.content.res.AssetManager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_WELCOME = 0
        const val TYPE_FEATURES = 1
        const val TYPE_DUAL_MOCKUP = 2
        const val TYPE_GET_STARTED = 3
    }

    override fun getItemViewType(position: Int) = when (slides[position]) {
        is SlideType.Welcome -> TYPE_WELCOME
        is SlideType.Features -> TYPE_FEATURES
        is SlideType.DualMockup -> TYPE_DUAL_MOCKUP
        is SlideType.GetStarted -> TYPE_GET_STARTED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_WELCOME -> WelcomeVH(inflater.inflate(R.layout.slide_welcome, parent, false))
            TYPE_FEATURES -> FeaturesVH(inflater.inflate(R.layout.slide_features, parent, false))
            TYPE_DUAL_MOCKUP -> DualMockupVH(inflater.inflate(R.layout.slide_dual_mockup, parent, false))
            else -> GetStartedVH(inflater.inflate(R.layout.slide_get_started, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val slide = slides[position]) {
            is SlideType.Welcome -> (holder as WelcomeVH).bind(slide)
            is SlideType.Features -> (holder as FeaturesVH).bind(slide)
            is SlideType.DualMockup -> (holder as DualMockupVH).bind(slide)
            is SlideType.GetStarted -> (holder as GetStartedVH).bind(slide)
        }
    }

    override fun getItemCount() = slides.size

    private fun loadAssetImage(name: String): android.graphics.Bitmap? {
        return try {
            val stream = assets.open(name)
            val bmp = BitmapFactory.decodeStream(stream)
            stream.close()
            bmp
        } catch (e: Exception) { null }
    }

    inner class WelcomeVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(slide: SlideType.Welcome) {
            // Round logo
            val ivLogo = itemView.findViewById<ImageView>(R.id.ivLogo)
            ivLogo.setImageResource(slide.logoRes)
            val roundBg = android.graphics.drawable.GradientDrawable()
            roundBg.shape = android.graphics.drawable.GradientDrawable.OVAL
            roundBg.setColor(Color.WHITE)
            ivLogo.background = roundBg
            ivLogo.clipToOutline = true
            ivLogo.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

            itemView.findViewById<TextView>(R.id.tvTitle).text = slide.title
            itemView.findViewById<TextView>(R.id.tvDesc).text = slide.description
            itemView.findViewById<TextView>(R.id.tvBimDesc).text =
                "Bahasa Isyarat Malaysia (BIM) is the official sign language used by the Deaf community in Malaysia. With over 100,000 Deaf individuals in Malaysia, BIM plays a vital role in everyday communication and social inclusion."
        }
    }

    inner class FeaturesVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(slide: SlideType.Features) {
            // Layout is hardcoded in slide_features.xml — nothing dynamic needed
        }
    }

    inner class DualMockupVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(slide: SlideType.DualMockup) {
            // Top row: image left, text right
            val ivTop = itemView.findViewById<ImageView>(R.id.ivTopImage)
            itemView.findViewById<TextView>(R.id.tvTopTitle).text = slide.topTitle
            itemView.findViewById<TextView>(R.id.tvTopDesc).text = slide.topDesc
            loadAssetImage(slide.topImage)?.let { ivTop.setImageBitmap(it) }

            // Bottom row: text left, image right
            val ivBottom = itemView.findViewById<ImageView>(R.id.ivBottomImage)
            itemView.findViewById<TextView>(R.id.tvBottomTitle).text = slide.bottomTitle
            itemView.findViewById<TextView>(R.id.tvBottomDesc).text = slide.bottomDesc
            loadAssetImage(slide.bottomImage)?.let { ivBottom.setImageBitmap(it) }
        }
    }

    inner class GetStartedVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(slide: SlideType.GetStarted) {
            val ivRock = itemView.findViewById<ImageView>(R.id.ivRock)
            loadAssetImage("rock.png")?.let { ivRock.setImageBitmap(it) }
            itemView.findViewById<TextView>(R.id.tvTitle).text = slide.title
            itemView.findViewById<TextView>(R.id.tvDesc).text = slide.description
        }
    }
}