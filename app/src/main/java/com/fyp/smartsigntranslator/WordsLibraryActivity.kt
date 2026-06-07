package com.fyp.smartsigntranslator

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class GestureWord(
    val label: String,
    val category: String
)

class WordsLibraryActivity : AppCompatActivity() {

    private lateinit var rvWords: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var filterChips: LinearLayout
    private lateinit var tvWordCount: TextView

    private val categories = listOf("All", "Alphabet", "Family", "Places", "Food & Drinks", "Animals", "Verbs", "Others")

    private val allWords = listOf(
        GestureWord("A", "Alphabet"), GestureWord("B", "Alphabet"), GestureWord("C", "Alphabet"),
        GestureWord("D", "Alphabet"), GestureWord("E", "Alphabet"), GestureWord("F", "Alphabet"),
        GestureWord("G", "Alphabet"), GestureWord("H", "Alphabet"), GestureWord("I", "Alphabet"),
        GestureWord("J", "Alphabet"), GestureWord("K", "Alphabet"), GestureWord("L", "Alphabet"),
        GestureWord("M", "Alphabet"), GestureWord("N", "Alphabet"), GestureWord("O", "Alphabet"),
        GestureWord("P", "Alphabet"), GestureWord("Q", "Alphabet"), GestureWord("R", "Alphabet"),
        GestureWord("S", "Alphabet"), GestureWord("T", "Alphabet"), GestureWord("U", "Alphabet"),
        GestureWord("V", "Alphabet"), GestureWord("W", "Alphabet"), GestureWord("X", "Alphabet"),
        GestureWord("Y", "Alphabet"), GestureWord("Z", "Alphabet"),
        // Family
        GestureWord("EMAK", "Family"), GestureWord("AYAH", "Family"),
        GestureWord("ABANG", "Family"), GestureWord("KAMI", "Family"), GestureWord("KAMU", "Family"),
        GestureWord("AWAK", "Family"), GestureWord("SAYA", "Family"),
        // Places
        GestureWord("HOSPITAL", "Places"), GestureWord("HOTEL", "Places"),
        GestureWord("SEKOLAH", "Places"), GestureWord("KEDAI", "Places"),
        GestureWord("PEJABAT", "Places"), GestureWord("RUMAH", "Places"),
        GestureWord("DOBI", "Places"), GestureWord("FERI", "Others"),
        // Food & Drinks
        GestureWord("NASI", "Food & Drinks"), GestureWord("ROTI", "Food & Drinks"),
        GestureWord("AIR", "Food & Drinks"), GestureWord("MILO", "Food & Drinks"),
        GestureWord("TEH", "Food & Drinks"), GestureWord("MINUM", "Verbs"),
        GestureWord("MAKAN", "Food & Drinks"), GestureWord("TELUR", "Food & Drinks"),
        // Animals
        GestureWord("KUCING", "Animals"), GestureWord("ANJING", "Animals"),
        GestureWord("LEMBU", "Animals"), GestureWord("ARNAB", "Animals"),
        // Verbs
        GestureWord("BACA", "Verbs"), GestureWord("BELAJAR", "Verbs"),
        GestureWord("BELI", "Verbs"), GestureWord("BERI", "Verbs"),
        GestureWord("TIDUR", "Verbs"), GestureWord("PERGI", "Verbs"),
        GestureWord("LIHAT", "Verbs"), GestureWord("LUKIS", "Verbs"),
        GestureWord("MAIN", "Verbs"), GestureWord("AMBIL", "Verbs"),
        GestureWord("POTONG", "Verbs"), GestureWord("MANDI", "Verbs"),
        // Others
        GestureWord("HAI", "Others"), GestureWord("MAAF", "Others"),
        GestureWord("TERIMA KASIH", "Others"), GestureWord("INI", "Others"),
        GestureWord("DAN", "Others"), GestureWord("DENGAN", "Others"),
        GestureWord("KE", "Others"), GestureWord("MANA", "Others"),
        GestureWord("SIAPA", "Others"), GestureWord("KENAPA", "Others"),
        GestureWord("BAGAIMANA", "Others"), GestureWord("BILA", "Others"),
        GestureWord("BERAPA", "Others"), GestureWord("KERJA", "Verbs"),
        GestureWord("KATIL", "Others"), GestureWord("BUKU", "Others"),
        GestureWord("MAJALAH", "Others"), GestureWord("MOTOSIKAL", "Others"),
        GestureWord("PUKUL", "Verbs")
    )

    private var selectedCategory = "All"
    private var searchQuery = ""
    private lateinit var adapter: WordsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_words_library)

        rvWords = findViewById(R.id.rvWords)
        etSearch = findViewById(R.id.etSearch)
        filterChips = findViewById(R.id.filterChips)
        tvWordCount = findViewById(R.id.tvWordCount)

        applyTheme()
        setupFilterChips()
        setupRecyclerView()
        setupSearch()

        findViewById<ImageButton>(R.id.btnBackLibrary).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun applyTheme() {
        val root = findViewById<ConstraintLayout>(R.id.rootWordsLibrary)
        ThemeManager.apply(activity = this, root = root)
    }

    private fun setupFilterChips() {
        val dp = resources.displayMetrics.density
        categories.forEach { cat ->
            val chip = TextView(this)
            chip.text = cat
            chip.textSize = 13f
            chip.setPadding((14 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = (8 * dp).toInt()
            chip.layoutParams = lp
            chip.setOnClickListener {
                selectedCategory = cat
                updateChipStyles()
                filterWords()
            }
            filterChips.addView(chip)
        }
        updateChipStyles()
    }

    private fun updateChipStyles() {
        val dp = resources.displayMetrics.density
        for (i in 0 until filterChips.childCount) {
            val chip = filterChips.getChildAt(i) as TextView
            val isSelected = chip.text == selectedCategory
            val bg = GradientDrawable()
            bg.cornerRadius = 32f
            if (isSelected) {
                bg.setColor(Color.parseColor("#10B981"))
                chip.setTextColor(Color.WHITE)
            } else {
                bg.setColor(Color.TRANSPARENT)
                bg.setStroke((1 * dp).toInt(), Color.parseColor("#3A3A3A"))
                chip.setTextColor(Color.parseColor("#71717A"))
            }
            chip.background = bg
        }
    }

    private fun setupRecyclerView() {
        adapter = WordsAdapter(getFilteredWords()) { word ->
            showWordDetail(word)
        }
        rvWords.layoutManager = GridLayoutManager(this, 2)
        rvWords.adapter = adapter
        updateWordCount()
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                filterWords()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun getFilteredWords(): List<GestureWord> {
        return allWords.filter { word ->
            val matchCat = selectedCategory == "All" || word.category == selectedCategory
            val matchSearch = searchQuery.isEmpty() || word.label.contains(searchQuery, ignoreCase = true)
            matchCat && matchSearch
        }.sortedBy { it.label }
    }

    private fun filterWords() {
        val filtered = getFilteredWords()
        adapter.updateList(filtered)
        updateWordCount()
    }

    private fun updateWordCount() {
        tvWordCount.text = "${adapter.itemCount} gestures"
    }

    private fun showWordDetail(word: GestureWord) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_word_detail)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        val ivImage = dialog.findViewById<ImageView>(R.id.ivDetailImage)
        val tvLabel = dialog.findViewById<TextView>(R.id.tvDetailLabel)
        val tvCategory = dialog.findViewById<TextView>(R.id.tvDetailCategory)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseDetail)

        tvLabel.text = word.label
        tvCategory.text = word.category

        // Load image from assets
        try {
            val imgName = "ic_${word.label.lowercase().replace(" ", "_")}.jpg"
            val stream = assets.open("gesture_images/$imgName")
            val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
            ivImage.setImageBitmap(bitmap)
            stream.close()
        } catch (e: Exception) {
            ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}

class WordsAdapter(
    private var words: List<GestureWord>,
    private val onClick: (GestureWord) -> Unit
) : RecyclerView.Adapter<WordsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.ivWordImage)
        val tvLabel: TextView = view.findViewById(R.id.tvWordLabel)
        val tvCategory: TextView = view.findViewById(R.id.tvWordCategory)
        val card: LinearLayout = view.findViewById(R.id.wordCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val word = words[position]
        holder.tvLabel.text = word.label
        holder.tvCategory.text = word.category

        // Load image from assets
        try {
            val imgName = "ic_${word.label.lowercase().replace(" ", "_")}.jpg"
            val stream = holder.itemView.context.assets.open("gesture_images/$imgName")
            val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
            holder.ivImage.setImageBitmap(bitmap)
            stream.close()
        } catch (e: Exception) {
            holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.card.setOnClickListener { onClick(word) }
    }

    override fun getItemCount() = words.size

    fun updateList(newList: List<GestureWord>) {
        words = newList
        notifyDataSetChanged()
    }
}