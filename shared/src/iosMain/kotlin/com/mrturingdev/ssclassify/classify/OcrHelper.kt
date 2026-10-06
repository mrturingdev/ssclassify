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
import platform.Vision.VNBarcodeObservation
import platform.Vision.VNDetectBarcodesRequest
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/** One Vision pass over a screenshot: its text lines, and the payload of a QR or barcode if it has one. */
class VisionScan(val lines: List<OcrLine>, val barcodePayload: String?)

/** Apple Vision OCR and barcode detection over a Photos asset; blocking, call off the main thread. */
class OcrHelper {
    private val manager = PHImageManager.defaultManager()
    private val options = PHImageRequestOptions().apply {
        deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
        resizeMode = PHImageRequestOptionsResizeModeFast
        synchronous = true
        networkAccessAllowed = false
    }

    /**
     * Text lines (boxes flipped to the shared top-left normalized space) and the
     * first barcode's payload, from one image load and one Vision request handler.
     * Null when the image can't be loaded or Vision fails, as opposed to a screen with no text.
     */
    fun scan(asset: PHAsset, maxDimension: Double = 1600.0): VisionScan? {
        var image: UIImage? = null
        manager.requestImageForAsset(
            asset,
            CGSizeMake(maxDimension, maxDimension),
            PHImageContentModeAspectFit,
            options,
        ) { result, _ -> image = result }
        val cgImage = image?.CGImage ?: return null
        val handler = VNImageRequestHandler(cgImage, emptyMap<Any?, Any?>())

        val request = VNRecognizeTextRequest().apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            usesLanguageCorrection = true
        }
        val barcodes = VNDetectBarcodesRequest()
        // False when either request fails; keep whatever the other one found (a QR survives an OCR failure).
        val ok = handler.performRequests(listOf(request, barcodes), null)
        if (!ok && request.results == null && barcodes.results == null) return null

        val payload = barcodes.results.orEmpty()
            .firstNotNullOfOrNull { (it as? VNBarcodeObservation)?.payloadStringValue?.takeIf(String::isNotBlank) }
        val lines = request.results.orEmpty().mapNotNull { result ->
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
        return VisionScan(lines, payload)
    }
}
