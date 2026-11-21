package com.example.signlanguagetranslatorapp

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class SignItem {
    /** Small heading shown above the letters of one word. */
    data class Word(val text: String) : SignItem()
    data class Letter(val char: Char) : SignItem()
}

/** Shows words as a heading followed by a wrapping grid of sign cards. */
class SignAdapter(
    private val loadBitmap: (Char) -> Bitmap?,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<SignItem> = emptyList()

    fun submit(newItems: List<SignItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun spanSize(position: Int, columns: Int): Int =
        if (items[position] is SignItem.Word) columns else 1

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = if (items[position] is SignItem.Word) TYPE_WORD else TYPE_LETTER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_WORD) {
            WordHolder(inflater.inflate(R.layout.item_word, parent, false) as TextView)
        } else {
            LetterHolder(inflater.inflate(R.layout.item_letter, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SignItem.Word -> (holder as WordHolder).label.text = item.text
            is SignItem.Letter -> {
                val h = holder as LetterHolder
                h.image.setImageBitmap(loadBitmap(item.char))
                h.label.text = item.char.uppercase()
                h.itemView.contentDescription = "Sign for ${item.char.uppercase()}"
            }
        }
    }

    private class WordHolder(val label: TextView) : RecyclerView.ViewHolder(label)

    private class LetterHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.signImage)
        val label: TextView = view.findViewById(R.id.letterLabel)
    }

    private companion object {
        const val TYPE_WORD = 0
        const val TYPE_LETTER = 1
    }
}
