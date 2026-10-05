package com.mrturingdev.ssclassify.classify

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions

class TensorFlowVisionHelper(context: Context) {
    private val classifier: ImageClassifier

    // The model has no label metadata, so categories come back as bare indexes.
    private val labels: List<String> =
        context.assets.open("labels_mobilenet_quant_v1_224.txt").bufferedReader().use { it.readLines() }

    init {
        val baseOptions = BaseOptions.builder().setNumThreads(2).build()
        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(baseOptions)
            .setMaxResults(3)
            .build()
        classifier = ImageClassifier.createFromFileAndOptions(context, "mobilenet_v1_1.0_224_quant.tflite", options)
    }

    fun classify(bitmap: Bitmap): List<ClassificationResult> {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results = classifier.classify(tensorImage)
        
        val classifications = mutableListOf<ClassificationResult>()
        for (classificationsResult in results) {
            for (category in classificationsResult.categories) {
                val label = category.label.takeUnless { it.isBlank() || it.all(Char::isDigit) }
                    ?: labels.getOrNull(category.index)
                    ?: continue
                classifications.add(ClassificationResult(label, category.score))
            }
        }
        return classifications
    }

    fun close() {
        classifier.close()
    }
}
