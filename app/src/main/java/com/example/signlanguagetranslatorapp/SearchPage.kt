package com.example.signlanguagetranslatorapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.core.graphics.Insets
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText

/** Text -> sign images (assets/letters/a.png ... z.png), updated live while typing. */
class SearchPage(
    private val activity: ComponentActivity,
    val root: View,
    onBack: () -> Unit,
) {
    private val input: TextInputEditText = root.findViewById(R.id.textEntred)
    private val bitmapCache = HashMap<Char, Bitmap?>()
    private val adapter = SignAdapter(::loadLetter)

    init {
        root.findViewById<ImageButton>(R.id.backButton).setOnClickListener { onBack() }

        val grid = GridLayoutManager(activity, COLUMNS).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = adapter.spanSize(position, COLUMNS)
            }
        }
        root.findViewById<RecyclerView>(R.id.signList).apply {
            layoutManager = grid
            adapter = this@SearchPage.adapter
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = render(s?.toString().orEmpty())
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                true
            } else {
                false
            }
        }
    }

    fun applyInsets(bars: Insets, imeBottom: Int) {
        root.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, imeBottom))
    }

    fun hideKeyboard() {
        input.clearFocus()
        val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun render(text: String) {
        val items = ArrayList<SignItem>()
        for (token in text.lowercase().split(Regex("\\s+"))) {
            val letters = token.filter { it in 'a'..'z' }
            if (letters.isEmpty()) continue
            items.add(SignItem.Word(letters.uppercase()))
            letters.forEach { items.add(SignItem.Letter(it)) }
        }
        adapter.submit(items)
    }

    /** Decodes at half size (plenty for a ~110dp card) and keeps the result for reuse. */
    private fun loadLetter(char: Char): Bitmap? = bitmapCache.getOrPut(char) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            activity.assets.open("letters/$char.png").use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val COLUMNS = 3
    }
}
