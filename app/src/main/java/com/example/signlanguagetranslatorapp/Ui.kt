package com.example.signlanguagetranslatorapp

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/** The app is dark-only, so keep the status/navigation bar icons light whatever the system theme is. */
fun ComponentActivity.edgeToEdgeDark() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
}

fun ComponentActivity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
