package com.example.signlanguagetranslatorapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CameraActivity : AppCompatActivity() {
    private val requestPermissionCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        requestCameraPermission()

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

    private fun openCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            val preview = Preview.Builder().build()
            val previewView = findViewById<PreviewView>(R.id.previewView)

            try{
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                )
                preview.surfaceProvider = previewView.surfaceProvider
            }catch(exception: Exception){
                println(exception.message)
            }
        }, ContextCompat.getMainExecutor(this))
    }
}