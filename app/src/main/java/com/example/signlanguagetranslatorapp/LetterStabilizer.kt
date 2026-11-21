package com.example.signlanguagetranslatorapp

/**
 * Turns a noisy stream of per-frame predictions into typed letters.
 *
 * A letter is typed once it has been predicted [framesToCommit] frames in a row with at
 * least [minConfidence]. The same letter is not typed again until the hand has left the
 * view for [framesToRelease] frames or a different letter has been typed in between
 * (so "LL" = show L, lower the hand, show L again).
 */
class LetterStabilizer(
    private val framesToCommit: Int = 6,
    private val framesToRelease: Int = 6,
    val minConfidence: Float = 0.5f,
) {
    private var candidate: Char? = null
    private var candidateFrames = 0
    private var lastTyped: Char? = null
    private var noHandFrames = 0

    /** @param letter the predicted letter, or null when no hand was found in the frame. */
    @Synchronized
    fun onFrame(letter: Char?, confidence: Float): Char? {
        if (letter == null) {
            candidate = null
            candidateFrames = 0
            if (++noHandFrames >= framesToRelease) lastTyped = null
            return null
        }
        noHandFrames = 0
        if (confidence < minConfidence) {
            candidate = null
            candidateFrames = 0
            return null
        }
        if (letter == candidate) {
            candidateFrames++
        } else {
            candidate = letter
            candidateFrames = 1
        }
        if (candidateFrames >= framesToCommit && letter != lastTyped) {
            lastTyped = letter
            return letter
        }
        return null
    }

    /** 0..1: how close the current candidate letter is to being typed (for the hold-steady indicator). */
    @Synchronized
    fun progress(): Float =
        if (candidate == null) 0f else (candidateFrames.toFloat() / framesToCommit).coerceAtMost(1f)

    @Synchronized
    fun reset() {
        candidate = null
        candidateFrames = 0
        lastTyped = null
        noHandFrames = 0
    }
}
