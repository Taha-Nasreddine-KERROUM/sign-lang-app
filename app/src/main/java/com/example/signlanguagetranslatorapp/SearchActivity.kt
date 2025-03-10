
package com.example.signlanguagetranslatorapp

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
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
import java.io.File

@SuppressLint("StaticFieldLeak")
private lateinit var userTxt: EditText
@SuppressLint("StaticFieldLeak")
private lateinit var analyzeButton: ImageButton
@SuppressLint("StaticFieldLeak")
private lateinit var resultText: TextView
@SuppressLint("StaticFieldLeak")
private lateinit var signImagesContainer: LinearLayout
private const val TAG = "SearchActivity"

class SearchActivity : AppCompatActivity() {

    private fun copyAssetImagesToStorage() {
        try {
            val assetManager = assets
            val destDir = File(getExternalFilesDir(null), "asl_alphabet_test")
            // Skip if directory already exists and has files
            if (destDir.exists() && destDir.listFiles()?.isNotEmpty() == true) {
                return
            }
            // Create directory if it doesn't exist
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            // Copy each image from assets to external storage
            val assetImages = assetManager.list("asl_alphabet_test") ?: return
            for (filename in assetImages) {
                val inStream = assetManager.open("asl_alphabet_test/$filename")
                val outFile = File(destDir, filename)
                val outStream = outFile.outputStream()
                inStream.copyTo(outStream)
                inStream.close()
                outStream.close()
            }

            Log.d(TAG, "Copied ${assetImages.size} images to external storage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset images: ${e.message}", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search2)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        copyAssetImagesToStorage()

        // Initialisation of components
        userTxt = findViewById(R.id.textEntred)
        analyzeButton = findViewById(R.id.analyzeButton)
        resultText = findViewById(R.id.resultText)
        signImagesContainer = findViewById(R.id.signImagesContainer)

        // Set button click listener
        analyzeButton.setOnClickListener {
            // Get input text
            val inputText = userTxt.text.toString().trim()

            if (inputText.isEmpty()) {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Clear previous results
            signImagesContainer.removeAllViews()

            // Process each character and display corresponding sign images
            displaySignLanguageImages(inputText)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displaySignLanguageImages(text: String) {
        try {
            // Clear existing views
            signImagesContainer.removeAllViews()

            // Get the directory where sign language images are stored
            // This assumes images are in assets/asl_alphabet_test and have been copied to internal storage
            val imagesDir = File(getExternalFilesDir(null), "asl_alphabet_test")

            if (!imagesDir.exists() || !imagesDir.isDirectory) {
                Log.e(TAG, "Images directory not found: ${imagesDir.absolutePath}")
                resultText.text = "Error: Sign language images directory not found"
                return
            }

            // Get list of all available image files
            val imageFiles = imagesDir.listFiles() ?: emptyArray()

            // Create a map of character to image file for quick lookup
            val characterToImageMap = mutableMapOf<Char, File>()
            for (file in imageFiles) {
                val name = file.nameWithoutExtension
                if (name.length == 1) {
                    characterToImageMap[name[0].uppercaseChar()] = file
                }
            }

            // Process each character in the input text
            var foundAnyImages = false
            for (char in text) {
                val upperChar = char.uppercaseChar()

                if (upperChar in 'A'..'Z') {
                    val imageFile = characterToImageMap[upperChar]

                    if (imageFile != null && imageFile.exists()) {
                        // Create and add image view
                        val imageView = ImageView(this)
                        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                        imageView.setImageBitmap(bitmap)

                        // Set size and margins
                        val params = LinearLayout.LayoutParams(300, 300)
                        params.marginEnd = 16
                        imageView.layoutParams = params

                        // Add character text below the image
                        val charTextView = TextView(this)
                        charTextView.text = char.toString()
                        charTextView.textAlignment = TextView.TEXT_ALIGNMENT_CENTER

                        // Container for image and label
                        val container = LinearLayout(this)
                        container.orientation = LinearLayout.VERTICAL
                        container.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        container.addView(imageView)
                        container.addView(charTextView)

                        // Add to main container
                        signImagesContainer.addView(container)
                        foundAnyImages = true
                    } else {
                        // Create text placeholder for missing image
                        val textView = TextView(this)
                        textView.text = char.toString()
                        textView.textSize = 30f
                        textView.setPadding(30, 30, 30, 30)
                        signImagesContainer.addView(textView)
                    }
                } else if (char.isWhitespace()) {
                    // Add space between words
                    val spacer = TextView(this)
                    spacer.text = " "
                    spacer.textSize = 30f
                    spacer.setPadding(40, 0, 40, 0)
                    signImagesContainer.addView(spacer)
                }
            }

            if (!foundAnyImages) {
                resultText.text = "No matching sign language images found for: $text"
            } else {
                resultText.text = "Showing sign language for: $text"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying sign language images: ${e.message}", e)
            resultText.text = "Error: ${e.message}"
        }
    }
}

