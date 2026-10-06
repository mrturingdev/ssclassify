@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mrturingdev.ssclassify.classify

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGSizeMake
import platform.Photos.PHAsset
import platform.Photos.PHImageContentModeAspectFit
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsResizeModeFast
import platform.UIKit.UIImage
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/** Apple Vision OCR over a Photos asset; blocking, call off the main thread. */
class OcrHelper {
    private val manager = PHImageManager.defaultManager()
    private val options = PHImageRequestOptions().apply {
        deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
        resizeMode = PHImageRequestOptionsResizeModeFast
        synchronous = true
        networkAccessAllowed = false
    }

    /** Vision text lines with boxes flipped to the shared top-left normalized space. */
    /** Null when the image can't be loaded or Vision fails, as opposed to a screen with no text. */
    fun recognizeLines(asset: PHAsset, maxDimension: Double = 1600.0): List<OcrLine>? {
        var image: UIImage? = null
        manager.requestImageForAsset(
            asset,
            CGSizeMake(maxDimension, maxDimension),
            PHImageContentModeAspectFit,
            options,
        ) { result, _ -> image = result }
        val cgImage = image?.CGImage ?: return null

        val request = VNRecognizeTextRequest().apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            usesLanguageCorrection = true
        }
        val handler = VNImageRequestHandler(cgImage, emptyMap<Any?, Any?>())
        if (!handler.performRequests(listOf(request), null)) return null

        return request.results.orEmpty().mapNotNull { result ->
            val observation = result as? VNRecognizedTextObservation ?: return@mapNotNull null
            val text = (observation.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string
                ?: return@mapNotNull null
            observation.boundingBox.useContents {
                // Vision: normalized, origin bottom-left.
                val top = 1.0 - (origin.y + size.height)
                OcrLine(
                    text = text,
                    left = origin.x.toFloat(),
                    top = top.toFloat(),
                    right = (origin.x + size.width).toFloat(),
                    bottom = (top + size.height).toFloat(),
                )
            }
        }
    }
}
