package com.example.signlanguagetranslatorapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity(), HandTrackingAnalyzer.PredictionListener {
    private val requestPermissionCode = 100
    private lateinit var previewView: PreviewView
    private lateinit var resultTextView: TextView
    private lateinit var handTrackingAnalyzer: HandTrackingAnalyzer
    private lateinit var cameraExecutor: ExecutorService
    private var detectedText = StringBuilder()
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // Initialize views
        previewView = findViewById(R.id.previewView)
        resultTextView = findViewById(R.id.resultTextView)
        gestureDetector = GestureDetector(this, SwipeGestureListener())

        // Set up clear button
        val clearButton = findViewById<ImageButton>(R.id.clearButton)
        clearButton.setOnClickListener {
            detectedText.clear()
            resultTextView.text = ""
        }

        // Initialize hand tracking analyzer
        handTrackingAnalyzer = HandTrackingAnalyzer(this)
        handTrackingAnalyzer.setPredictionListener(this)

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request camera permission
        requestCameraPermission()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val sWIPETHRESHOLD = 100
        private val sWIPEVELOCITYTHRESHOLD = 100
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 != null) {
                val diffx = e1.x - e2.x
                if (kotlin.math.abs(diffx) > sWIPETHRESHOLD && kotlin.math.abs(velocityX) > sWIPEVELOCITYTHRESHOLD) {
                    if (diffx > 0) {
                        val intent = Intent(this@CameraActivity, SearchActivity::class.java)
                        startActivity(intent)
                    }
                    else{
                        Log.d("SwipeGesture","Swiped Left")
                        finish()
                    }
                    return true
                }
            }
            return false

        }
    }

    private fun requestCameraPermission() {
        if(ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                requestPermissionCode
            )
        } else {
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == requestPermissionCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Set up the preview use case
            val preview = Preview.Builder().build()
            preview.surfaceProvider = previewView.surfaceProvider

            // Set up the image analysis use case
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Set up the analyzer
            val signLanguageAnalyzer = SignLanguageImageAnalyzer(handTrackingAnalyzer)
            imageAnalysis.setAnalyzer(cameraExecutor, signLanguageAnalyzer)

            // Select camera
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

            } catch(e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onPredictionResult(letter: String) {
        runOnUiThread {
            // Update UI with the predicted letter
            if (letter == "space") {
                detectedText.append(" ")
            } else if (letter != "nothing") {
                // Only append if the letter is not "nothing"
                detectedText.append(letter)
            }

            resultTextView.text = detectedText.toString()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handTrackingAnalyzer.close()
    }
}