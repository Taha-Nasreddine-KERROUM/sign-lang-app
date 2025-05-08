package com.example.signlanguagetranslatorapp

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class MainActivity : AppCompatActivity(){
    private lateinit var gestureDetector: GestureDetector
    private val SWIPE_THRESHOLD = 100
    private val SWIPE_VELOCITY_THRESHOLD = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        gestureDetector = GestureDetector(this, object : SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if(e1 == null) return false

                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if(abs(diffX) > abs(diffY)){
                    // Horizontal swipe
                    if(abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD){
                        if(diffX > 0){
                            //left to right
                        }else{
                            //right to left
                            onSwipeLeft()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return event?.let { gestureDetector.onTouchEvent(it) }?: false || super.onTouchEvent(event)
    }

    private fun onSwipeLeft() {
        val intent = Intent(this, CameraActivity::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34+)
            startActivity(intent, ActivityOptions.makeCustomAnimation(
                this,
                R.anim.slide_in_right,   // NEW activity enters from right
                R.anim.slide_out_left // CURRENT activity exits to left
            ).toBundle())
        } else {
            // Legacy support (API < 34)
            @Suppress("DEPRECATION")
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(
                R.anim.slide_in_right,   // NEW enters from right
                R.anim.slide_out_left  // CURRENT exits left
            )
        }
    }
}