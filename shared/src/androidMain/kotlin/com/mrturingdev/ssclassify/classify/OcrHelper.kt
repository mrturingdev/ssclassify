package com.mrturingdev.ssclassify.classify

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OcrHelper {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** ML Kit text lines with boxes normalized to the bitmap; empty on failure. */
    fun recognizeLines(bitmap: Bitmap): List<OcrLine> {
        return try {
            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
            val width = bitmap.width.toFloat()
            val height = bitmap.height.toFloat()
            result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(line.text, box.left / width, box.top / height, box.right / width, box.bottom / height)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun close() {
        recognizer.close()
    }
}
