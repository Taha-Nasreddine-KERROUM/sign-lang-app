package com.example.signlanguagetranslatorapp

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.print.PrintAttributes.Resolution
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.nio.FloatBuffer
import com.example.signlanguagetranslatorapp.OverlayView
import com.google.mediapipe.framework.image.BitmapImageBuilder
import java.io.ByteArrayOutputStream

class CameraActivity : AppCompatActivity() {
    private val requestPermissionCode = 100
    private lateinit var handLandmarker: HandLandmarker
    private lateinit var overlayView: OverlayView
    private lateinit var env: OrtEnvironment
    private lateinit var session: OrtSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        requestCameraPermission()
        // Initialize ONNX runtime
        env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        val modelBytes = assets.open("model.onnx").readBytes()
        session = env.createSession(modelBytes, sessionOptions)
        // Build Mediapipe base options with the model asset path
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()

        // Configure the handLandmarker
        val handLandmarkerOptions = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener{ result, inputImage ->
                processLandmarkResult(result)
            }
            .build()
        handLandmarker = HandLandmarker.createFromOptions(this, handLandmarkerOptions)
    }

    private fun requestCameraPermission(){
        if(ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                requestPermissionCode
            )
        }else{
            openCamera()
        }
    }

    private fun onRequestPermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResult: IntArray
    ){
        if(requestCode == requestPermissionCode && grantResult.isNotEmpty() && grantResult[0] == PackageManager.PERMISSION_GRANTED){
            openCamera()
        }else{
            requestCameraPermission()
        }
    }

    private fun isCameraAvailable(): Boolean{
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList
        return cameraIds.isNotEmpty()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun openCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val preview = Preview.Builder().build()
            val previewView = findViewById<PreviewView>(R.id.previewView)

            overlayView = OverlayView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            (previewView.parent as ViewGroup).addView(overlayView)
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1280,720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                ))
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()
            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)){ imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null){
                    try {
                        val bitmap = imageProxyToBitmap(imageProxy)
                        val mpImage = BitmapImageBuilder(bitmap).build()
                        val timestamp = System.currentTimeMillis()
                        handLandmarker.detectAsync(mpImage,timestamp)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        imageProxy.close()
                    }
                }
                imageProxy.close()
            }
            try{
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                preview.surfaceProvider = previewView.surfaceProvider
            }catch(exception: Exception){
                println(exception.message)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap{
        // Get YUV image buffer
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        // Byte Array for YUV data
        val nv21 = ByteArray(ySize+uSize+vSize)

        // Copy the Y, U, and V planes into the NV21 byte array
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21,ImageFormat.NV21,imageProxy.width,imageProxy.height,null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0,0,imageProxy.width,imageProxy.height),100,out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.size)
    }

    fun processLandmarkResult(result: HandLandmarkerResult){
        val landmarkLists = result.landmarks()
        if(landmarkLists.isEmpty()) return

        val data = mutableListOf<Float>()
        if(landmarkLists.size == 2){
            for(handLandmarks in landmarkLists){
                val xs = handLandmarks.map { it.x() }
                val ys = handLandmarks.map { it.y() }
                val xMin = xs.minOrNull() ?: 0f
                val yMin = ys.minOrNull() ?: 0f
                for(lm in handLandmarks){
                    data.add(lm.x() - xMin)
                    data.add(lm.y() - yMin)
                }
            }
        }else{
            val handLandmarks = landmarkLists[0]
            val xs = handLandmarks.map { it.x() }
            val ys = handLandmarks.map { it.y() }
            val xMin = xs.minOrNull() ?: 0f
            val yMin = ys.minOrNull() ?: 0f
            for(lm in handLandmarks){
                data.add(lm.x() - xMin)
                data.add(lm.y() - yMin)
            }
            while (data.size < 84) data.add(0f)
        }
        val inputArray = data.toFloatArray()
        val floatBuffer = FloatBuffer.wrap(inputArray)

        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1,84))
        val outputArray: FloatArray
        try {
            val results = session.run(mapOf(session.inputNames.first() to inputTensor))
            val outputTensor = results[0] as OnnxTensor
            Log.d("CameraActivity", "Output tensor info: ${outputTensor.info}")
            val predictedChar: String
            when(outputTensor.info.onnxType){
                TensorInfo.OnnxTensorType.ONNX_TENSOR_ELEMENT_DATA_TYPE_STRING -> {
                    val outputValue = outputTensor.value as? Array<String>
                    if(outputValue != null && outputValue.isNotEmpty()){
                        predictedChar = outputValue[0]
                    }else{
                        Log.e("CameraActivity", "Output tensor is null or empty.")
                        return
                    }
                }
                TensorInfo.OnnxTensorType.ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT -> {
                    val outputBuffer = outputTensor.floatBuffer
                    if (outputBuffer == null){
                        Log.e("CameraActivity", "Output buffer is null. Check ONNX model and input.")
                        return
                    }
                    outputArray = FloatArray(outputTensor.info.shape.fold(1L, Long::times).toInt()).also { outputBuffer.get(it) }
                    Log.d("CameraActivity", "Output array: ${outputArray.joinToString()}")
                    val maxIdx = outputArray.indices.maxByOrNull { outputArray[it] } ?: -1
                    val characterLabels = arrayOf(
                        "A","B","C","D","E","F","G","H","I","J","K","L","M","N","Nothing","O",
                        "P","Q","R","S","Space","T","U","V","W","X","Y","Z"
                    )
                    predictedChar = if (maxIdx >= 0 && maxIdx < characterLabels.size) characterLabels[maxIdx] else "?"
                }
                else -> {
                    Log.e("CameraActivity", "Unsupported tensor type: ${outputTensor.info.onnxType}")
                    return
                }
            }
            runOnUiThread{
                overlayView.updateResults(result,predictedChar)
            }

        } catch (e: Exception) {
            Log.e("CameraActivity", "Model inference failed: ${e.message}", e)
            return
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session.close()
        env.close()
    }
}