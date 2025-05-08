package com.example.signlanguagetranslatorapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
): View(context) {
    private var boxes = listOf<RectF>()
    private var label: String = ""
    private val boxPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 4f }
    private val textPaint = Paint().apply { color = Color.RED; textSize = 60f }

    fun updateResults(result: HandLandmarkerResult, predictedChar: String){
        val allPoints = result.landmarks().flatten()
        if(allPoints.isNotEmpty()){
            val xMin = allPoints.minOf { it.x() } * width
            val xMax = allPoints.maxOf { it.x() } * width
            val yMin = allPoints.minOf { it.y() } * height
            val yMax = allPoints.maxOf { it.y() } * height
            boxes = listOf(RectF(xMin,yMin,xMax,yMax))
            label = predictedChar
        }else {
            boxes = emptyList()
            label = ""
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for(box in boxes){
            canvas.drawRect(box,boxPaint)
            canvas.drawText(label, box.left,box.top - 10f,textPaint)
        }
    }
}