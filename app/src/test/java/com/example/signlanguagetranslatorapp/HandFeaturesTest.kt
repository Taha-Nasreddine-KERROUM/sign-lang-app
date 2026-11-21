package com.example.signlanguagetranslatorapp

import org.junit.Assert.assertEquals
import org.junit.Test

class HandFeaturesTest {
    private val lx = FloatArray(21) { 0.2f + it * 0.02f }
    private val ly = FloatArray(21) { 0.9f - it * 0.03f }

    @Test
    fun landscape640x480IsTheOriginalPythonFormula() {
        val f = HandFeatures.build(lx, ly, 640, 480)
        val minX = lx.min()
        val minY = ly.min()
        for (i in 0 until 21) {
            assertEquals(lx[i] - minX, f[2 * i], 1e-6f)
            assertEquals(ly[i] - minY, f[2 * i + 1], 1e-6f)
        }
    }

    @Test
    fun portraitFrameWithSamePixelSizeGivesSameFeatures() {
        val landscape = HandFeatures.build(lx, ly, 640, 480)
        // same pixel layout expressed relative to a 480x640 portrait frame
        val px = FloatArray(21) { lx[it] * 640f / 480f }
        val py = FloatArray(21) { ly[it] * 480f / 640f }
        val portrait = HandFeatures.build(px, py, 480, 640)
        for (i in 0 until 42) assertEquals(landscape[i], portrait[i], 1e-5f)
    }

    @Test
    fun smallestCoordinatesAreZero() {
        val f = HandFeatures.build(lx, ly, 480, 640)
        assertEquals(0f, (0 until 21).minOf { f[2 * it] }, 0f)
        assertEquals(0f, (0 until 21).minOf { f[2 * it + 1] }, 0f)
    }
}
