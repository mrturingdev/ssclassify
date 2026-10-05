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

    const val LABEL_QR_CODE = "QR Code"

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
        return when (valueType) {
            Barcode.TYPE_URL -> {
                val host = url?.url?.let { extractHost(it) } ?: raw
                QrResult(
                    subLabel = LABEL_QR_CODE,
                    description = "QR Code linking to: $host",
                    detail = raw,
                )
            }
            Barcode.TYPE_CONTACT_INFO -> {
                val contact = contactInfo
                val name = contact?.name?.formattedName?.takeIf { it.isNotBlank() } ?: "Unknown person"
                val org = contact?.organization?.takeIf { it.isNotBlank() }
                val desc = if (org != null) "Contact QR: $name ($org)" else "Contact QR: $name"
                QrResult(subLabel = LABEL_QR_CODE, description = desc, detail = raw)
            }
            Barcode.TYPE_PHONE -> {
                val phone = phone?.number ?: raw
                QrResult(subLabel = LABEL_QR_CODE, description = "Phone QR: $phone", detail = raw)
            }
            Barcode.TYPE_EMAIL -> {
                val addr = email?.address ?: raw
                QrResult(subLabel = LABEL_QR_CODE, description = "Email QR: $addr", detail = raw)
            }
            Barcode.TYPE_WIFI -> {
                val ssid = wifi?.ssid ?: "Unknown network"
                QrResult(subLabel = LABEL_QR_CODE, description = "Wi-Fi QR for network: $ssid", detail = raw)
            }
            Barcode.TYPE_GEO -> {
                QrResult(subLabel = LABEL_QR_CODE, description = "Location QR code", detail = raw)
            }
            Barcode.TYPE_CALENDAR_EVENT -> {
                val summary = calendarEvent?.summary?.takeIf { it.isNotBlank() } ?: "event"
                QrResult(subLabel = LABEL_QR_CODE, description = "Calendar QR: $summary", detail = raw)
            }
            Barcode.TYPE_SMS -> {
                val number = sms?.phoneNumber ?: raw
                QrResult(subLabel = LABEL_QR_CODE, description = "SMS QR to: $number", detail = raw)
            }
            else -> {
                // Detect common payment QR patterns in raw value
                val description = detectPaymentOrAppQr(raw)
                    ?: if (raw.length <= 120) "QR Code: $raw" else "QR Code"
                QrResult(subLabel = LABEL_QR_CODE, description = description, detail = raw)
            }
        }
    }

    /**
     * Heuristically identifies payment / app QR codes from raw text patterns.
     * Examples: UPI deep links, PayPal, Venmo, Cash App, Google Pay, PhonePe.
     */
    private fun detectPaymentOrAppQr(raw: String): String? {
        val lower = raw.lowercase()
        return when {
            lower.startsWith("upi://") || lower.contains("pa=") && lower.contains("upi") -> {
                val payee = Regex("pn=([^&]+)").find(raw)?.groupValues?.get(1)
                    ?: Regex("pa=([^&]+)").find(raw)?.groupValues?.get(1)
                if (payee != null) "UPI Payment QR for $payee" else "UPI Payment QR"
            }
            lower.contains("paypal.com") -> "PayPal Payment QR"
            lower.contains("venmo.com") || lower.startsWith("venmo://") -> "Venmo Payment QR"
            lower.contains("cash.app") || lower.startsWith("cashapp://") -> "Cash App Payment QR"
            lower.contains("gpay") || lower.contains("googlepay") -> "Google Pay QR"
            lower.contains("phonepe") -> "PhonePe Payment QR"
            lower.contains("paytm") -> "Paytm Payment QR"
            lower.contains("bhim") -> "BHIM UPI Payment QR"
            lower.contains("bitcoin:") -> "Bitcoin Payment QR"
            lower.contains("ethereum:") -> "Ethereum Payment QR"
            lower.startsWith("otpauth://") -> "Authenticator App QR (2FA)"
            lower.contains("instagram.com") -> "Instagram QR Code"
            lower.contains("snapchat.com") || lower.contains("snapchat") -> "Snapchat QR Code"
            lower.contains("wa.me") || lower.contains("whatsapp.com") -> "WhatsApp QR Code"
            lower.contains("t.me") || lower.contains("telegram") -> "Telegram QR Code"
            lower.contains("twitter.com") || lower.contains("x.com") -> "Twitter/X QR Code"
            lower.contains("linkedin.com") -> "LinkedIn QR Code"
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube QR Code"
            lower.contains("spotify.com") -> "Spotify QR Code"
            else -> null
        }
    }

    private fun extractHost(url: String): String = try {
        val noScheme = url.substringAfter("://")
        noScheme.substringBefore("/").substringBefore("?")
    } catch (_: Exception) {
        url.take(60)
    }
}

data class QrResult(
    val subLabel: String,
    val description: String,
    val detail: String,
)
