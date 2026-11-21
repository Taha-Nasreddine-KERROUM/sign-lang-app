package com.example.signlanguagetranslatorapp

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * CameraX analyzer: converts each frame into an upright bitmap and hands it to the tracker.
 *
 * Runs on the executor given to ImageAnalysis.setAnalyzer (a single thread), so the tracker
 * is never called concurrently. [onResult] is invoked on that same thread.
 *
 * Important: the frame must be rotated by imageInfo.rotationDegrees, otherwise MediaPipe sees
 * a sideways hand (the old code skipped this). It is deliberately NOT mirrored for the front
 * camera: the training images were raw, un-mirrored webcam frames.
 */
class SignLanguageImageAnalyzer(
    private val tracker: HandTrackingAnalyzer,
    private val onResult: (HandTrackingAnalyzer.Prediction?) -> Unit,
    /** Frames are skipped while this returns false (e.g. the search page is showing). */
    private val isActive: () -> Boolean = { true },
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedMs = 0L
    private var lastTimestampMs = 0L

    override fun analyze(image: ImageProxy) {
        try {
            if (!isActive()) return
            val now = SystemClock.uptimeMillis()
            if (now - lastAnalyzedMs < MIN_INTERVAL_MS) return
            lastAnalyzedMs = now

            val timestamp = maxOf(now, lastTimestampMs + 1) // MediaPipe needs strictly increasing timestamps
            lastTimestampMs = timestamp

            val rotation = image.imageInfo.rotationDegrees
            val bitmap = image.toBitmap()
            val upright = if (rotation == 0) bitmap else rotate(bitmap, rotation)

            onResult(tracker.process(upright, timestamp))
        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis failed", e)
        } finally {
            image.close()
        }
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private companion object {
        const val TAG = "SignAnalyzer"
        const val MIN_INTERVAL_MS = 80L // ~12 fps is plenty for static letters
    }
}
