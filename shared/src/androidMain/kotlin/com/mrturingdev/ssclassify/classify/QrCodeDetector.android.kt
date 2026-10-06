package com.mrturingdev.ssclassify.classify

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Detects QR codes (and other barcodes) in a bitmap via ML Kit.
 * Returns a [QrResult] when a QR / barcode is found, or null.
 */
object QrCodeDetector {

    /**
     * Scans [bitmap] for barcodes. Returns the first match or null.
     * Must be called from a coroutine (uses suspend for the ML Kit Task callback).
     */
    suspend fun detect(bitmap: Bitmap): QrResult? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val scanner = BarcodeScanning.getClient()
        return suspendCancellableCoroutine { cont ->
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    cont.resume(barcodes.firstOrNull()?.toQrResult())
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
                .addOnCompleteListener {
                    scanner.close()
                }
        }
    }

    private fun Barcode.toQrResult(): QrResult {
        val raw = rawValue ?: displayValue ?: ""
        // Payment codes first: the decoded payload names the payee and network reliably.
        val described = QrDescriber.describe(raw)
        if (described.subLabel == PaymentQrParser.SUB_CATEGORY) return described
        return when (valueType) {
            Barcode.TYPE_URL -> {
                val host = url?.url?.let { extractHost(it) } ?: raw
                QrResult(
                    subLabel = QrDescriber.LABEL_QR_CODE,
                    description = "QR Code linking to: $host",
                    detail = raw,
                )
            }
            Barcode.TYPE_CONTACT_INFO -> {
                val contact = contactInfo
                val name = contact?.name?.formattedName?.takeIf { it.isNotBlank() } ?: "Unknown person"
                val org = contact?.organization?.takeIf { it.isNotBlank() }
                val desc = if (org != null) "Contact QR: $name ($org)" else "Contact QR: $name"
                QrResult(subLabel = QrDescriber.LABEL_QR_CODE, description = desc, detail = raw)
            }
            Barcode.TYPE_PHONE -> {
                val phone = phone?.number ?: raw
                QrResult(subLabel = QrDescriber.LABEL_QR_CODE, description = "Phone QR: $phone", detail = raw)
            }
            Barcode.TYPE_EMAIL -> {
                val addr = email?.address ?: raw
                QrResult(subLabel = QrDescriber.LABEL_QR_CODE, description = "Email QR: $addr", detail = raw)
            }
            Barcode.TYPE_WIFI -> {
                val ssid = wifi?.ssid ?: "Unknown network"
                QrResult(subLabel = QrDescriber.LABEL_QR_CODE, description = "Wi-Fi QR for network: $ssid", detail = raw)
            }
            Barcode.TYPE_GEO -> {
                QrResult(subLabel = QrDescriber.LABEL_QR_CODE, description = "Location QR code", detail = raw)
            }
            Barcode.TYPE_CALENDAR_EVENT -> {
                val summary = calendarEvent?.summary?.takeIf { it.isNotBlank() } ?: "event"
                QrResult(subLabel = QrDescriber.LABEL_QR_CODE, description = "Calendar QR: $summary", detail = raw)
            }
            Barcode.TYPE_SMS -> {
                val number = sms?.phoneNumber ?: raw
                QrResult(subLabel = QrDescriber.LABEL_QR_CODE, description = "SMS QR to: $number", detail = raw)
            }
            // Text payloads (Wi-Fi strings, otpauth, app links...) are described the same way as on iOS.
            else -> described
        }
    }

    private fun extractHost(url: String): String = try {
        val noScheme = url.substringAfter("://")
        noScheme.substringBefore("/").substringBefore("?")
    } catch (_: Exception) {
        url.take(60)
    }
}
