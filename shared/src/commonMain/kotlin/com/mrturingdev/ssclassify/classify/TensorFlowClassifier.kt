package com.mrturingdev.ssclassify.classify

data class ClassificationResult(val label: String, val confidence: Float)

expect class TensorFlowClassifier {
    fun close()
}
