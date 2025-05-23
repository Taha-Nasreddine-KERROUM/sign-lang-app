package com.example.signlanguagetranslatorapp


import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import kotlin.math.min

class HandTrackingAnalyzer(private val context: Context) {

    private var tflite: Interpreter? = null
    private var handLandmarker: HandLandmarker? = null
    private val labelsMap = mapOf(
        0 to "A", 1 to "B", 2 to "C", 3 to "D", 4 to "E", 5 to "F", 6 to "G", 7 to "H", 8 to "I",
        9 to "J", 10 to "K", 11 to "L", 12 to "M", 13 to "N", 14 to "nothing", 15 to "O", 16 to "P", 17 to "Q",
        18 to "R", 19 to "S", 20 to "space", 21 to "T", 22 to "U", 23 to "V", 24 to "W", 25 to "X", 26 to "Y", 27 to "Z"
    )

    // Interface for prediction results
    interface PredictionListener {
        fun onPredictionResult(letter: String)
    }

    private var predictionListener: PredictionListener? = null

    fun setPredictionListener(listener: PredictionListener) {
        predictionListener = listener
    }

    init {
        try {
            // Initialize TFLite interpreter
            val tfliteModel = FileUtil.loadMappedFile(context, "model.tflite")
            val options = Interpreter.Options()
            options.setNumThreads(4)
            tflite = Interpreter(tfliteModel, options)

            // Initialize MediaPipe HandLandmarker
            setupHandLandmarker()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupHandLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumHands(2)  // Detect up to 2 hands
            .setMinHandDetectionConfidence(0.5f)
            .setRunningMode(RunningMode.IMAGE)
            .build()

        try {
            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun processImage(bitmap: Bitmap) {
        // Convert the bitmap to MediaPipe image format
        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(0)
            .build()

        // Create MPImage from bitmap
        val image = BitmapImageBuilder(bitmap).build()



        // Process the image with MediaPipe HandLandmarker
        val result = handLandmarker?.detect(image, imageProcessingOptions) ?: return

        // Extract landmarks and make prediction
        if (result.landmarks().isNotEmpty()) {
            val handLandmarks = result.landmarks()

            // Convert landmarks to the format expected by the model
            val inputFeatures = prepareInputFeatures(handLandmarks)

            // Make prediction using TFLite model
            val outputProbabilities = Array(1) { FloatArray(28) } // 28 classes (A-Z + space + nothing)
            tflite?.run(inputFeatures, outputProbabilities)

            // Get the most probable class
            val maxIndex = outputProbabilities[0].indices.maxByOrNull { outputProbabilities[0][it] } ?: -1
            if (maxIndex >= 0) {
                val predictedLetter = labelsMap[maxIndex] ?: "unknown"
                predictionListener?.onPredictionResult(predictedLetter)
            }
        }
    }

    private fun prepareInputFeatures(handLandmarksList: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>): Array<FloatArray> {
        // Initialize array for input features
        val inputFeatures = Array(1) { FloatArray(84) } // Assuming model expects 84 features (21 landmarks * 2 coordinates * 2 hands)

        // Fill with zeros initially
        inputFeatures[0].fill(0.0f)

        if (handLandmarksList.isNotEmpty()) {
            // Process first hand
            val firstHandLandmarks = handLandmarksList[0]

            // Find min x and y for normalization (similar to Python code)
            var minX = 1.0f
            var minY = 1.0f

            for (landmark in firstHandLandmarks) {
                minX = min(minX, landmark.x())
                minY = min(minY, landmark.y())
            }

            // Fill in normalized coordinates
            for (i in firstHandLandmarks.indices) {
                val landmark = firstHandLandmarks[i]
                inputFeatures[0][i * 2] = landmark.x() - minX
                inputFeatures[0][i * 2 + 1] = landmark.y() - minY
            }

            // Process second hand if present
            if (handLandmarksList.size > 1) {
                val secondHandLandmarks = handLandmarksList[1]

                // Find min x and y for second hand
                minX = 1.0f
                minY = 1.0f

                for (landmark in secondHandLandmarks) {
                    minX = min(minX, landmark.x())
                    minY = min(minY, landmark.y())
                }

                // Fill in normalized coordinates for second hand
                for (i in secondHandLandmarks.indices) {
                    val landmark = secondHandLandmarks[i]
                    inputFeatures[0][42 + i * 2] = landmark.x() - minX
                    inputFeatures[0][42 + i * 2 + 1] = landmark.y() - minY
                }
            }
        }

        return inputFeatures
    }

    fun close() {
        tflite?.close()
        handLandmarker?.close()
    }
}