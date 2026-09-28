@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.agy.imagecategorizer.classify

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

    fun extractText(asset: PHAsset, maxDimension: Double = 1600.0): String {
        var image: UIImage? = null
        manager.requestImageForAsset(
            asset,
            CGSizeMake(maxDimension, maxDimension),
            PHImageContentModeAspectFit,
            options,
        ) { result, _ -> image = result }
        val cgImage = image?.CGImage ?: return ""

        val request = VNRecognizeTextRequest().apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            usesLanguageCorrection = true
        }
        val handler = VNImageRequestHandler(cgImage, emptyMap<Any?, Any?>())
        if (!handler.performRequests(listOf(request), null)) return ""

        return request.results.orEmpty()
            .mapNotNull { (it as? VNRecognizedTextObservation)?.topCandidates(1u)?.firstOrNull() as? VNRecognizedText }
            .joinToString("\n") { it.string }
    }
}
