package com.example.signlanguagetranslatorapp

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * The whole app: a two-page pager.
 *   page 0 = camera (sign -> text, rear camera by default)   <- the app opens here
 *   page 1 = search (text -> sign)                            <- swipe left; swipe right to come back
 * The pages follow your finger while you swipe.
 */
class CameraActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var cameraPage: View
    private lateinit var searchPage: SearchPage

    private lateinit var previewView: PreviewView
    private lateinit var resultTextView: TextView
    private lateinit var outputScroll: ScrollView
    private lateinit var liveLetter: TextView
    private lateinit var caption: TextView
    private lateinit var holdProgress: LinearProgressIndicator
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var flipButton: ImageButton
    private lateinit var topBar: View
    private lateinit var topScrim: View
    private lateinit var bottomSheet: View
    private lateinit var cameraExecutor: ExecutorService

    private val stabilizer = LetterStabilizer()
    private val detectedText = StringBuilder()

    private var tracker: HandTrackingAnalyzer? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // The rear camera is the default; the flip button switches to the selfie camera.
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    /** Hand tracking only runs while the camera page is the one you are looking at. */
    @Volatile
    private var analysisEnabled = true

    private var bars: Insets = Insets.NONE
    private var imeBottom = 0

    private val backToCamera = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            pager.setCurrentItem(PAGE_CAMERA, true)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdgeDark()
        setContentView(R.layout.activity_camera)

        lensFacing = savedInstanceState?.getInt(STATE_LENS, CameraSelector.LENS_FACING_BACK)
            ?: CameraSelector.LENS_FACING_BACK

        pager = findViewById(R.id.pager)

        val inflater = LayoutInflater.from(this)
        cameraPage = inflater.inflate(R.layout.page_camera, null)
        val searchRoot = inflater.inflate(R.layout.page_search, null)
        val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
        cameraPage.layoutParams = ViewGroup.LayoutParams(matchParent, matchParent)
        searchRoot.layoutParams = ViewGroup.LayoutParams(matchParent, matchParent)

        setUpCameraPage()
        searchPage = SearchPage(this, searchRoot) { pager.setCurrentItem(PAGE_CAMERA, true) }

        pager.adapter = PagesAdapter(listOf(cameraPage, searchRoot))
        pager.offscreenPageLimit = 1 // keep both pages alive so the camera never restarts while swiping
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val onCamera = position == PAGE_CAMERA
                analysisEnabled = onCamera
                backToCamera.isEnabled = !onCamera
                stabilizer.reset()
                if (onCamera) {
                    searchPage.hideKeyboard()
                } else {
                    showNoHand()
                }
            }
        })
        onBackPressedDispatcher.addCallback(this, backToCamera)

        ViewCompat.setOnApplyWindowInsetsListener(pager) { _, insets ->
            bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            applyInsets()
            insets
        }

        renderText()
        showLoading()

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Loading the two models takes a moment, so do it off the main thread.
        cameraExecutor.execute {
            try {
                val created = HandTrackingAnalyzer.create(applicationContext)
                runOnUiThread {
                    if (isDestroyed) {
                        created.close()
                    } else {
                        tracker = created
                        ensureCameraPermission()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model initialisation failed", e)
                runOnUiThread {
                    caption.setText(R.string.model_load_failed)
                    Toast.makeText(this, e.message ?: getString(R.string.model_load_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setUpCameraPage() {
        previewView = cameraPage.findViewById(R.id.previewView)
        // TextureView-based preview moves smoothly with the page while you swipe
        // (the default SurfaceView would lag behind the sliding page).
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        resultTextView = cameraPage.findViewById(R.id.resultTextView)
        outputScroll = cameraPage.findViewById(R.id.outputScroll)
        liveLetter = cameraPage.findViewById(R.id.liveLetter)
        caption = cameraPage.findViewById(R.id.caption)
        holdProgress = cameraPage.findViewById(R.id.holdProgress)
        statusText = cameraPage.findViewById(R.id.statusText)
        statusDot = cameraPage.findViewById(R.id.statusDot)
        flipButton = cameraPage.findViewById(R.id.flipButton)
        topBar = cameraPage.findViewById(R.id.topBar)
        topScrim = cameraPage.findViewById(R.id.topScrim)
        bottomSheet = cameraPage.findViewById(R.id.bottomSheet)

        cameraPage.findViewById<View>(R.id.searchButton).setOnClickListener {
            pager.setCurrentItem(PAGE_SEARCH, true)
        }
        flipButton.setOnClickListener { flipCamera() }

        cameraPage.findViewById<View>(R.id.clearButton).setOnClickListener {
            detectedText.clear()
            stabilizer.reset()
            renderText()
        }
        cameraPage.findViewById<View>(R.id.spaceButton).setOnClickListener {
            detectedText.append(' ')
            renderText()
        }
        cameraPage.findViewById<View>(R.id.backspaceButton).setOnClickListener {
            if (detectedText.isNotEmpty()) detectedText.deleteCharAt(detectedText.length - 1)
            renderText()
        }
    }

    private fun applyInsets() {
        topBar.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = bars.top + dp(8) }
        topScrim.updateLayoutParams<ViewGroup.LayoutParams> { height = bars.top + dp(120) }
        bottomSheet.setPadding(dp(16), dp(14), dp(16), bars.bottom + dp(12))
        searchPage.applyInsets(bars, imeBottom)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_LENS, lensFacing)
    }

    private fun ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        if (tracker == null) return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                // Only offer the switch button when the phone really has both cameras.
                flipButton.visibility = if (hasFront && hasBack) View.VISIBLE else View.GONE
                if (lensFacing == CameraSelector.LENS_FACING_FRONT && !hasFront) {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                } else if (lensFacing == CameraSelector.LENS_FACING_BACK && !hasBack) {
                    lensFacing = CameraSelector.LENS_FACING_FRONT
                }
                bindCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Could not start camera", e)
                Toast.makeText(this, getString(R.string.camera_error, e.message), Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** (Re)binds preview + analysis to whichever camera [lensFacing] points at. */
    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val activeTracker = tracker ?: return
        try {
            val preview = Preview.Builder().build()
            preview.surfaceProvider = previewView.surfaceProvider

            // The analysis frames are deliberately NOT mirrored for either camera (the model was
            // trained on raw frames); PreviewView mirrors only what you see for the selfie camera.
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(
                cameraExecutor,
                SignLanguageImageAnalyzer(activeTracker, ::onFrame) { analysisEnabled },
            )

            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview, analysis)
            showNoHand()
        } catch (e: Exception) {
            Log.e(TAG, "Could not bind camera", e)
            Toast.makeText(this, getString(R.string.camera_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        stabilizer.reset()
        flipButton.animate().rotationBy(180f).setDuration(300).start()
        bindCamera()
    }

    /** Called on the camera analysis thread for every analysed frame. */
    private fun onFrame(prediction: HandTrackingAnalyzer.Prediction?) {
        val typed = stabilizer.onFrame(prediction?.letter, prediction?.confidence ?: 0f)
        val progress = stabilizer.progress()
        runOnUiThread {
            if (isDestroyed || !analysisEnabled) return@runOnUiThread
            if (prediction == null) {
                showNoHand()
            } else {
                showHand(prediction, progress)
            }
            if (typed != null) {
                detectedText.append(typed)
                renderText()
                liveLetter.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                liveLetter.animate().scaleX(1.12f).scaleY(1.12f).setDuration(90).withEndAction {
                    liveLetter.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }.start()
            }
        }
    }

    private fun showLoading() {
        setStatus(R.string.status_loading, R.color.idle)
        caption.setText(R.string.status_loading)
        holdProgress.setProgressCompat(0, false)
    }

    private fun showNoHand() {
        setStatus(R.string.status_no_hand, R.color.idle)
        liveLetter.text = "–"
        liveLetter.setBackgroundResource(R.drawable.bg_letter_tile_idle)
        caption.setText(R.string.caption_show_hand)
        holdProgress.setProgressCompat(0, false)
    }

    private fun showHand(p: HandTrackingAnalyzer.Prediction, progress: Float) {
        setStatus(R.string.status_hand, R.color.success)
        val sure = p.confidence >= stabilizer.minConfidence
        val percent = (p.confidence * 100).roundToInt()
        liveLetter.text = p.letter.toString()
        liveLetter.setBackgroundResource(if (sure) R.drawable.bg_letter_tile else R.drawable.bg_letter_tile_idle)
        caption.text = when {
            !sure -> "${getString(R.string.caption_unsure)} · $percent%"
            progress >= 1f -> getString(R.string.caption_added)
            else -> "${getString(R.string.caption_hold)} · $percent%"
        }
        holdProgress.setProgressCompat((progress * 100).roundToInt(), true)
    }

    private fun setStatus(textRes: Int, colorRes: Int) {
        statusText.setText(textRes)
        statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }

    private fun renderText() {
        resultTextView.text = detectedText.toString() // empty -> the hint is shown
        outputScroll.post { outputScroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        val toClose = tracker
        tracker = null
        // Close on the analysis thread so we never close the model while a frame is being processed.
        cameraExecutor.execute { toClose?.close() }
        cameraExecutor.shutdown()
    }

    /** Hands the two ready-made page views to the pager (position 0 = camera, 1 = search). */
    private class PagesAdapter(private val pages: List<View>) : RecyclerView.Adapter<PageHolder>() {
        override fun getItemCount() = pages.size
        override fun getItemViewType(position: Int) = position
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PageHolder(pages[viewType])
        override fun onBindViewHolder(holder: PageHolder, position: Int) = Unit
    }

    private class PageHolder(view: View) : RecyclerView.ViewHolder(view)

    private companion object {
        const val TAG = "CameraActivity"
        const val STATE_LENS = "lens_facing"
        const val PAGE_CAMERA = 0
        const val PAGE_SEARCH = 1
    }
}
