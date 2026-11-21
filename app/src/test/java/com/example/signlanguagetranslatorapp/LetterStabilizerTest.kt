package com.example.signlanguagetranslatorapp

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterStabilizerTest {

    private fun run(s: LetterStabilizer, vararg frames: Pair<Char?, Float>): String {
        val out = StringBuilder()
        for ((c, conf) in frames) s.onFrame(c, conf)?.let { out.append(it) }
        return out.toString()
    }

    private fun rep(c: Char?, n: Int, conf: Float = 0.9f) = Array(n) { c to conf }

    @Test
    fun heldLetterIsTypedOnlyOnce() {
        val s = LetterStabilizer(framesToCommit = 3, framesToRelease = 3)
        assertEquals("A", run(s, *rep('A', 20)))
    }

    @Test
    fun flickerIsIgnored() {
        val s = LetterStabilizer(framesToCommit = 3, framesToRelease = 3)
        assertEquals("", run(s, 'A' to 0.9f, 'B' to 0.9f, 'A' to 0.9f, 'B' to 0.9f, 'A' to 0.9f))
    }

    @Test
    fun sameLetterTwiceNeedsTheHandToLeaveInBetween() {
        val s = LetterStabilizer(framesToCommit = 3, framesToRelease = 3)
        assertEquals("AA", run(s, *rep('A', 5), *rep(null, 3), *rep('A', 5)))
    }

    @Test
    fun lowConfidenceIsNeverTyped() {
        val s = LetterStabilizer(framesToCommit = 3, framesToRelease = 3, minConfidence = 0.5f)
        assertEquals("", run(s, *rep('C', 10, conf = 0.2f)))
    }

    @Test
    fun differentLettersAreTypedInOrder() {
        val s = LetterStabilizer(framesToCommit = 3, framesToRelease = 3)
        assertEquals("BA", run(s, *rep('B', 4), *rep('A', 4)))
    }
}
