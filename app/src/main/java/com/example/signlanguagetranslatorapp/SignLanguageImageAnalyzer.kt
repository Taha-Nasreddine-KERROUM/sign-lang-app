package com.example.signlanguagetranslatorapp

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.Executors

class SignLanguageImageAnalyzer(
    private val handTrackingAnalyzer: HandTrackingAnalyzer
) : ImageAnalysis.Analyzer {

    private val executor = Executors.newSingleThreadExecutor()
    private var lastAnalyzedTimestamp = 0L
    private val analysisCooldown = 250L  // Analyze every 0.25 s

    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()

        // Only analyze frames at a reasonable rate to avoid overwhelming the device
        if (currentTimestamp - lastAnalyzedTimestamp >= analysisCooldown) {
            imageProxy.use { proxy ->
              //  val rotationDegrees = proxy.imageInfo.rotationDegrees

                // Convert image to bitmap
                val bitmap = proxy.toBitmap()

                // Process the bitmap in a separate thread to avoid blocking the camera
                executor.execute {
                    handTrackingAnalyzer.processImage(bitmap)
                }

                lastAnalyzedTimestamp = currentTimestamp
            }
        } else {
            imageProxy.close()
        }
    }

}