package com.example.signlanguagetranslatorapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.IOException

class SearchActivity : AppCompatActivity() {

    private lateinit var userTxt: EditText
    private lateinit var analyzeButton: ImageButton
    private lateinit var signImagesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialization
        userTxt = findViewById(R.id.textEntred)
        analyzeButton = findViewById(R.id.analyzeButton)
        signImagesContainer = findViewById(R.id.signImagesContainer)

        // button clicked
        analyzeButton.setOnClickListener {
            val inputText = userTxt.text.toString().trim()
            if (inputText.isEmpty()) {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Clear previous results
            signImagesContainer.removeAllViews()

            // call of display images function
            displaySignLanguageImages(inputText)
        }
    }


    @SuppressLint("SetTextI18n")
    private fun displaySignLanguageImages(text: String) {
        try {
            // Process each character in the input text
            for (char in text.lowercase()) {
                if (char in 'a'..'z') {
                    try {
                        // open image from assets
                        val inputStream = assets.open("letters/$char.png")
                        val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)

                        if (drawable != null) {
                            // Create ImageView
                            val imageView = ImageView(this)
                            imageView.setImageDrawable(drawable)

                            // layout parameters
                            val params = LinearLayout.LayoutParams(
                                300, // width
                                300  // height
                            )
                            params.marginEnd = 16 // space between images
                            imageView.layoutParams = params

                            // Add character label
                            val charLabel = TextView(this)
                            charLabel.text = char.toString().uppercase()
                            charLabel.textAlignment = TextView.TEXT_ALIGNMENT_CENTER

                            // Create a vertical container for image and label
                            val container = LinearLayout(this)
                            container.orientation = LinearLayout.VERTICAL
                            container.addView(imageView)
                            container.addView(charLabel)

                            // Add to main container
                            signImagesContainer.addView(container)
                        }
                    } catch (e: IOException) {
                        // Handle missing image
                        Toast.makeText(this, "Image not found for: $char", Toast.LENGTH_SHORT).show()
                    }
                } else if (char.isWhitespace()) {
                    // Add spacer for whitespace
                    val spacer = View(this)
                    spacer.layoutParams = LinearLayout.LayoutParams(50, 1)
                    signImagesContainer.addView(spacer)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error displaying images: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

}
