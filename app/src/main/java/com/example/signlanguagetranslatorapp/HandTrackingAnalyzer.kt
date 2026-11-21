package com.example.signlanguagetranslatorapp

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.Closeable

/**
 * MediaPipe hand landmarks  ->  [HandFeatures] (42 numbers)  ->  [RandomForestModel]  ->  letter.
 *
 * This is the same pipeline as "interface classifier.py" in the Python prototype.
 * Not thread-safe: call [process] from a single thread (the camera analysis thread).
 */
class HandTrackingAnalyzer private constructor(
    private val model: RandomForestModel,
    private val handLandmarker: HandLandmarker,
) : Closeable {

    data class Prediction(val letter: Char, val confidence: Float)

    /**
     * @param bitmap upright (already rotated) ARGB_8888 frame, NOT mirrored - exactly like a raw webcam frame.
     * @param timestampMs must be strictly increasing between calls.
     * @return the predicted letter, or null when no hand is visible.
     */
    fun process(bitmap: Bitmap, timestampMs: Long): Prediction? {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = handLandmarker.detectForVideo(mpImage, timestampMs)
        val hand = result.landmarks().firstOrNull() ?: return null
        if (hand.size != HandFeatures.LANDMARK_COUNT) return null

        val lx = FloatArray(HandFeatures.LANDMARK_COUNT) { hand[it].x() }
        val ly = FloatArray(HandFeatures.LANDMARK_COUNT) { hand[it].y() }
        val features = HandFeatures.build(lx, ly, bitmap.width, bitmap.height)

        val prediction = model.predict(features)
        if (prediction.label !in 0..25) return null
        return Prediction('A' + prediction.label, prediction.confidence)
    }

    override fun close() {
        handLandmarker.close()
    }

    companion object {
        private const val MODEL_ASSET = "rf_model.bin"
        private const val LANDMARKER_ASSET = "hand_landmarker.task"

        /** Loads both models. Throws [IllegalStateException] with a readable message on failure. */
        fun create(context: Context): HandTrackingAnalyzer {
            val model = try {
                context.assets.open(MODEL_ASSET).use { RandomForestModel.load(it) }
            } catch (e: Exception) {
                throw IllegalStateException("Could not load $MODEL_ASSET: ${e.message}", e)
            }

            val landmarker = try {
                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(LANDMARKER_ASSET).build())
                    .setNumHands(1) // the model was trained on a single hand (42 features)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setRunningMode(RunningMode.VIDEO)
                    .build()
                HandLandmarker.createFromOptions(context, options)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Could not load $LANDMARKER_ASSET (${e.message}). " +
                        "It is downloaded by the Gradle build - rebuild with an internet connection.", e
                )
            }
            return HandTrackingAnalyzer(model, landmarker)
        }
    }
}
