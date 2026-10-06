package com.mrturingdev.ssclassify.classify

/** What a screenshot's QR code is, for its sub-category, description and title. */
data class QrResult(
    val subLabel: String,
    val description: String,
    val detail: String,
    /** The payee of a payment QR, used as the screenshot's title. */
    val title: String? = null,
)

/**
 * Describes a decoded QR payload the same way on both platforms. Android's
 * scanner also reports typed values (contacts, calendar events), handled
 * there; everything else, and every code on iOS, comes through here.
 */
object QrDescriber {

    const val LABEL_QR_CODE = "QR Code"

    fun describe(payload: String): QrResult {
        val raw = payload.trim()
        PaymentQrParser.parse(raw)?.let { payment ->
            return QrResult(PaymentQrParser.SUB_CATEGORY, payment.description, raw, title = payment.payee)
        }
        val lower = raw.lowercase()
        val description = when {
            lower.startsWith("wifi:") ->
                Regex("""(?:^|;)S:((?:\\.|[^;])*)""").find(raw.substring(5))?.groupValues?.get(1)
                    ?.replace(Regex("""\\(.)"""), "$1")
                    ?.let { "Wi-Fi QR for network: $it" } ?: "Wi-Fi QR code"
            lower.startsWith("otpauth://") -> "Authenticator App QR (2FA)"
            lower.startsWith("tel:") -> "Phone QR: ${raw.substring(4)}"
            lower.startsWith("mailto:") -> "Email QR: ${raw.substring(7).substringBefore('?')}"
            lower.startsWith("smsto:") || lower.startsWith("sms:") -> "SMS QR to: ${raw.substringAfter(':').substringBefore(':')}"
            lower.startsWith("geo:") -> "Location QR code"
            else -> appQr(lower)
                ?: if (lower.startsWith("http://") || lower.startsWith("https://")) "QR Code linking to: ${host(raw)}"
                else if (raw.length <= 120) "QR Code: $raw" else "QR Code"
        }
        return QrResult(LABEL_QR_CODE, description, raw)
    }

    /** Well-known payment apps and sites, by pattern in the payload. */
    private fun appQr(lower: String): String? = when {
        lower.contains("paypal.com") -> "PayPal Payment QR"
        lower.contains("venmo.com") || lower.startsWith("venmo://") -> "Venmo Payment QR"
        lower.contains("cash.app") || lower.startsWith("cashapp://") -> "Cash App Payment QR"
        lower.contains("gpay") || lower.contains("googlepay") -> "Google Pay QR"
        lower.contains("phonepe") -> "PhonePe Payment QR"
        lower.contains("paytm") -> "Paytm Payment QR"
        lower.contains("bhim") -> "BHIM UPI Payment QR"
        lower.startsWith("bitcoin:") -> "Bitcoin Payment QR"
        lower.startsWith("ethereum:") -> "Ethereum Payment QR"
        lower.contains("instagram.com") -> "Instagram QR Code"
        lower.contains("snapchat.com") -> "Snapchat QR Code"
        lower.contains("wa.me") || lower.contains("whatsapp.com") -> "WhatsApp QR Code"
        lower.contains("t.me/") || lower.contains("telegram") -> "Telegram QR Code"
        lower.contains("twitter.com") || lower.contains("x.com/") -> "Twitter/X QR Code"
        lower.contains("linkedin.com") -> "LinkedIn QR Code"
        lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube QR Code"
        lower.contains("spotify.com") -> "Spotify QR Code"
        else -> null
    }

    private fun host(url: String): String = url.substringAfter("://").substringBefore("/").substringBefore("?")
}
