package com.example.signlanguagetranslatorapp

/**
 * Turns MediaPipe hand landmarks into the 42 numbers the model was trained on.
 *
 * Training ("create dataset.py") used, for the 21 landmarks of one hand:
 *     x_i - min(x), y_i - min(y)      with x, y normalised to a 640x480 webcam frame
 * interleaved as [x0, y0, x1, y1, ...].
 *
 * A phone delivers a portrait frame (e.g. 480x640), so normalised coordinates would be
 * scaled differently than in training. We therefore convert landmarks to pixels, scale
 * the frame so its short side is 480 px (the short side of the training frame) and
 * divide by 640 / 480. For a 640x480 landscape frame this is exactly the original maths.
 */
object HandFeatures {
    const val LANDMARK_COUNT = 21
    const val FEATURE_COUNT = LANDMARK_COUNT * 2

    private const val REF_WIDTH = 640f
    private const val REF_HEIGHT = 480f

    /**
     * @param lx normalised (0..1) x of each landmark, relative to the image that was analysed
     * @param ly normalised (0..1) y of each landmark
     */
    fun build(lx: FloatArray, ly: FloatArray, imageWidth: Int, imageHeight: Int): FloatArray {
        require(lx.size == LANDMARK_COUNT && ly.size == LANDMARK_COUNT) { "Need $LANDMARK_COUNT landmarks" }
        val scale = REF_HEIGHT / minOf(imageWidth, imageHeight)
        val xs = FloatArray(LANDMARK_COUNT) { lx[it] * imageWidth * scale / REF_WIDTH }
        val ys = FloatArray(LANDMARK_COUNT) { ly[it] * imageHeight * scale / REF_HEIGHT }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        for (i in 0 until LANDMARK_COUNT) {
            if (xs[i] < minX) minX = xs[i]
            if (ys[i] < minY) minY = ys[i]
        }
        val out = FloatArray(FEATURE_COUNT)
        for (i in 0 until LANDMARK_COUNT) {
            out[i * 2] = xs[i] - minX
            out[i * 2 + 1] = ys[i] - minY
        }
        return out
    }
}
