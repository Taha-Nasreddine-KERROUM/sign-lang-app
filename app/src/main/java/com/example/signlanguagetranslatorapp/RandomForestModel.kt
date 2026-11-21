package com.example.signlanguagetranslatorapp

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Pure-Kotlin evaluator for the scikit-learn RandomForestClassifier trained in the
 * Python prototype ("model v1_1/model.p"). The forest is exported by
 * `tools/export_rf_model.py` into `assets/rf_model.bin`; see that script for the format.
 *
 * Every leaf of this particular forest is pure, so soft voting (what sklearn's
 * predict does) is identical to majority voting over the trees.
 */
class RandomForestModel private constructor(
    val nFeatures: Int,
    private val classLabels: IntArray,
    private val trees: Array<Tree>,
) {
    private class Tree(
        val feature: ShortArray,
        val threshold: DoubleArray,
        val left: IntArray,   // for leaves: index of the winning class
        val right: IntArray,
    )

    /** [label] is the class id the model was trained with (0 = A ... 25 = Z). */
    data class Result(val label: Int, val confidence: Float)

    val treeCount: Int get() = trees.size

    fun predict(features: FloatArray): Result {
        require(features.size == nFeatures) {
            "Expected $nFeatures features but got ${features.size}"
        }
        val votes = IntArray(classLabels.size)
        for (tree in trees) {
            var node = 0
            while (true) {
                val f = tree.feature[node].toInt()
                if (f < 0) {
                    votes[tree.left[node]]++
                    break
                }
                // sklearn compares the float32 feature against a float64 threshold
                node = if (features[f].toDouble() <= tree.threshold[node]) tree.left[node] else tree.right[node]
            }
        }
        var best = 0
        for (i in 1 until votes.size) if (votes[i] > votes[best]) best = i
        return Result(classLabels[best], votes[best].toFloat() / trees.size)
    }

    companion object {
        private const val MAGIC = 0x52464D31 // "RFM1"

        fun load(stream: InputStream): RandomForestModel {
            DataInputStream(BufferedInputStream(stream, 1 shl 16)).use { input ->
                if (input.readInt() != MAGIC) throw IOException("rf_model.bin is not a valid forest file")
                val nFeatures = input.readInt()
                val nClasses = input.readInt()
                val classLabels = IntArray(nClasses) { input.readInt() }
                val nTrees = input.readInt()
                val trees = Array(nTrees) {
                    val n = input.readInt()
                    val feature = ShortArray(n) { input.readShort() }
                    val threshold = DoubleArray(n) { input.readDouble() }
                    val left = IntArray(n) { input.readInt() }
                    val right = IntArray(n) { input.readInt() }
                    Tree(feature, threshold, left, right)
                }
                return RandomForestModel(nFeatures, classLabels, trees)
            }
        }
    }
}
