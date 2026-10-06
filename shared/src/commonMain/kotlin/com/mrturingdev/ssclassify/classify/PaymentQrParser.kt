package com.mrturingdev.ssclassify.classify

/** A payment QR's meaning: who gets paid, through which network. */
data class PaymentQr(
    /** "Fonepay", "NepalPay", "eSewa", "UPI", "Bank account"... */
    val network: String,
    val payee: String?,
    val city: String? = null,
    /** With its currency, e.g. "NPR 699.00"; only dynamic QRs carry one. */
    val amount: String? = null,
) {
    /** "Fonepay payment QR · AAJU BHAIRAB PHARMA, Kirtipur · NPR 699.00" */
    val description: String
        get() = listOfNotNull(
            "$network payment QR",
            listOfNotNull(payee, city).joinToString(", ").takeIf { it.isNotEmpty() },
            amount,
        ).joinToString(" · ")
}

/**
 * Reads payment QR payloads as decoded by the barcode scanner, which is far
 * more reliable than the text printed around the code. Formats seen on real
 * Nepali screenshots: EMVCo merchant QRs (Fonepay, NepalPay, Alipay, card
 * networks), eSewa and bank-account JSON; plus UPI links. Account numbers are
 * never returned. Null when the payload is not a payment QR.
 */
object PaymentQrParser {

    /** Sub-category for screenshots whose QR is a payment code. */
    const val SUB_CATEGORY = "Payment QR"

    fun parse(payload: String): PaymentQr? {
        val text = payload.trim()
        return when {
            text.startsWith("000201") -> parseEmv(text)
            text.startsWith("upi://", ignoreCase = true) -> parseUpi(text)
            text.startsWith("{") -> parseJson(text)
            else -> null
        }
    }

    // --- EMVCo merchant-presented QR (TLV: 2-digit tag, 2-digit length, value) ---

    /** Merchant account templates (tags 26..51) carry a network identifier in sub-tag 00. */
    private val networksByIdentifier = listOf(
        "fonepay" to "Fonepay",
        "nchl" to "NepalPay",
        "nepalpay" to "NepalPay",
        "npci" to "UPI",
        "com.alipay" to "Alipay",
    )

    /** Primitive tags reserved by EMVCo for card networks. */
    private val cardNetworks = mapOf(
        "02" to "Visa", "03" to "Visa",
        "04" to "Mastercard", "05" to "Mastercard",
        "15" to "UnionPay", "16" to "UnionPay",
    )

    /** Domestic wallets first: they are what a payer in Nepal uses. */
    private val networkPriority = listOf("Fonepay", "NepalPay", "UPI", "Alipay", "Visa", "Mastercard", "UnionPay")

    private val currencies = mapOf("524" to "NPR", "356" to "INR", "840" to "USD", "156" to "CNY")

    private fun parseEmv(payload: String): PaymentQr? {
        if (!hasValidCrc(payload)) return null
        val fields = tlv(payload) ?: return null
        val networks = buildList {
            for ((tag, value) in fields) {
                cardNetworks[tag]?.let(::add)
                if (tag.toInt() in 26..51) {
                    val identifier = tlv(value)?.firstOrNull { it.first == "00" }?.second.orEmpty().lowercase()
                    networksByIdentifier.firstOrNull { (key, _) -> key in identifier }?.second?.let(::add)
                }
            }
        }
        val network = networks.minByOrNull { networkPriority.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } } ?: "Bank"
        val field = fields.toMap()
        val amount = field["54"]?.let { value -> listOfNotNull(field["53"]?.let { currencies[it] ?: it }, value).joinToString(" ") }
        return PaymentQr(
            network = network,
            payee = field["59"]?.trim()?.takeIf { it.isNotEmpty() },
            city = field["60"]?.trim()?.takeIf { it.isNotEmpty() },
            amount = amount,
        )
    }

    /** Top-level TLV fields in order, or null when the data is not well formed. */
    private fun tlv(data: String): List<Pair<String, String>>? {
        val fields = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < data.length) {
            if (i + 4 > data.length) return null
            val tag = data.substring(i, i + 2)
            val length = data.substring(i + 2, i + 4).toIntOrNull() ?: return null
            if (!tag.all { it.isDigit() } || i + 4 + length > data.length) return null
            fields += tag to data.substring(i + 4, i + 4 + length)
            i += 4 + length
        }
        return fields
    }

    /** The last field must be the CRC (tag 63, length 04) of everything before its value. */
    internal fun hasValidCrc(payload: String): Boolean {
        val marker = payload.length - 8
        if (marker < 0 || payload.substring(marker, marker + 4) != "6304") return false
        val expected = payload.substring(marker + 4).uppercase()
        return crc16(payload.substring(0, marker + 4)).toString(16).uppercase().padStart(4, '0') == expected
    }

    /** CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF), as EMVCo QR specifies. */
    internal fun crc16(data: String): Int {
        var crc = 0xFFFF
        for (byte in data.encodeToByteArray()) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return crc
    }

    // --- UPI deep link: upi://pay?pa=<vpa>&pn=<name>&am=<amount>&cu=<currency> ---

    private fun parseUpi(link: String): PaymentQr {
        val params = link.substringAfter('?', "").split('&').mapNotNull { pair ->
            pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> k.lowercase() to percentDecode(v) }
        }.toMap()
        val amount = params["am"]?.let { listOfNotNull(params["cu"] ?: "INR", it).joinToString(" ") }
        return PaymentQr(network = "UPI", payee = params["pn"] ?: params["pa"], amount = amount)
    }

    private fun percentDecode(value: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '%' && i + 2 < value.length -> {
                    value.substring(i + 1, i + 3).toIntOrNull(16)?.let { bytes += it.toByte(); i += 3 } ?: run { bytes += c.code.toByte(); i++ }
                }
                c == '+' -> { bytes += ' '.code.toByte(); i++ }
                else -> { bytes += c.toString().encodeToByteArray().toList(); i++ }
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    // --- JSON wallet / bank-account QRs ---

    private fun parseJson(json: String): PaymentQr? {
        fun field(key: String) = Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            field("eSewa_id") != null -> PaymentQr(network = "eSewa", payee = field("name"))
            field("accountNumber") != null -> PaymentQr(network = "Bank account", payee = field("accountName"))
            else -> null
        }
    }
}
