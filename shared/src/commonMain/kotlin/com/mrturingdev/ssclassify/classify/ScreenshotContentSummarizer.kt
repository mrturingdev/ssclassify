package com.mrturingdev.ssclassify.classify

import com.mrturingdev.ssclassify.model.ImageCategory
import com.mrturingdev.ssclassify.model.ImageRecord

object ScreenshotContentSummarizer {

    private val statusNoiseRegex = OcrTextProcessor.statusNoise

    private val tokenSplitRegex = Regex("[\\s|•·\\-]+")

    private val amountRegex = Regex(
        // Keywords are whole words: "rs" inside "users 300" is not rupees. Numbers keep
        // thousands and lakh grouping ("1,250.00", "1,25,000"), not just their first group.
        """(?i)(?:\b(?:total|grand\s*total|subtotal|amount|due|paid|price|balance|rs\.?|npr)|\$)\s*[:=-]?\s*([$€£₹]?\s*(?:\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:[.,]\d{1,2})?))"""
    )

    private val priorityAmountRegex = Regex(
        """(?i)\b(?:total|grand\s*total|amount|due|paid)\s*[:=-]?\s*([$€£₹]?\s*(?:\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:[.,]\d{1,2})?))"""
    )

    private val dateRegex = Regex(
        """(?i)(?:date|dated)?\s*[:=-]?\s*(\b\d{1,4}[/-]\d{1,2}[/-]\d{1,4}\b|\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2},?\s+\d{2,4}\b)"""
    )

    private val idRegex = Regex(
        // Whole-word keywords ("ref" inside "preference" is not one) and an ID with at least one digit.
        """(?i)\b(?:order|invoice|txn|transaction|ticket|pnr|id|reference|ref|bill)\b\s*(?:no\.?|id|#)?\s*[:=-]?\s*((?=[a-z0-9\-_]*\d)[a-z0-9\-_]{4,20})"""
    )

    /**
     * Generates a concise 1-2 line summary for preview cards and dialog headers.
     * Accepts [OcrText.summary]: "# " / "## " font-size headings lead the
     * title; plain text without markers keeps reading order.
     */
    fun summarizeDigest(ocrText: String, fallbackDescription: String? = null): String {
        val marked = ocrText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !isNoiseLine(headingText(it)) }

        if (marked.isEmpty()) {
            return fallbackDescription?.takeIf { it.isNotBlank() } ?: "No text detected"
        }

        val lines = marked.map(::headingText)
        // Larger-font lines first (stable, so reading order holds within a level).
        val byImportance = marked.sortedBy(::headingLevel).map(::headingText)

        // Check if there is an amount or headline line
        val amountLine = amountLine(lines)
        val titleLine = byImportance.firstOrNull { it.length in 4..60 && !amountRegex.containsMatchIn(it) } ?: byImportance.first()

        val digest = if (amountLine != null && amountLine != titleLine) {
            "$titleLine • $amountLine"
        } else {
            byImportance.take(2).joinToString(" • ")
        }

        return clip(digest)
    }

    private fun amountLine(lines: List<String>): String? =
        lines.lastOrNull { priorityAmountRegex.containsMatchIn(it) }
            ?: lines.firstOrNull { amountRegex.containsMatchIn(it) }

    private fun clip(text: String): String = if (text.length > 90) text.take(87).trimEnd() + "..." else text

    /**
     * Extracts a company/business name from OCR text (especially for QR code screenshots).
     * Checks:
     * 1. Labeled patterns like "Merchant: ...", "Payee: ...", "Company: ...", "Store: ...", "Account Name: ...".
     * 2. Lines with corporate/business entity indicators like "Pvt. Ltd.", "Inc.", "Cafe", "Restaurant", "Mart", etc.
     * 3. Prominent title/heading lines on the QR standee/image that are not generic QR instructions.
     */
    fun extractCompanyName(ocrText: String, allowGuess: Boolean = true): String? {
        val lines = ocrText.lines()
            .map { it.removePrefix("## ").removePrefix("# ").trim() }
            .filter { it.isNotBlank() && !isNoiseLine(it) }

        if (lines.isEmpty()) return null

        // 1. Explicit merchant / payee / company / store / account name label
        val labelRegex = Regex(
            """(?i)^\s*(?:merchant(?:\s*name)?|payee(?:\s*name)?|pay\s*to|company(?:\s*name)?|business(?:\s*name)?|account\s*name|store(?:\s*name)?|organization)\s*[:=-]\s*(.+)"""
        )
        for (line in lines) {
            val match = labelRegex.find(line)
            if (match != null) {
                val candidate = cleanCompanyName(match.groupValues[1])
                if (isValidCompanyName(candidate)) {
                    return candidate
                }
            }
        }

        // 2. Lines with corporate / legal suffixes
        val corporateSuffixRegex = Regex(
            """(?i)\b(?:pvt\.?\s*ltd\.?|ltd\.?|inc\.?|llc|corp\.?|corporation|gmbh|co\.)\b"""
        )
        for (line in lines) {
            if (corporateSuffixRegex.containsMatchIn(line) && !isGenericQrLine(line)) {
                val candidate = cleanCompanyName(line)
                if (isValidCompanyName(candidate)) {
                    return candidate
                }
            }
        }

        // 3. Lines with business / establishment keywords (Cafe, Coffee, Restaurant, Mart, Pharmacy, etc.)
        val businessKeywordRegex = Regex(
            // Not inside a hyphenated compound: "IN-STORE" or "e-shop" describe, they don't name.
            """(?i)(?<![-\w])(?:cafe|coffee|restaurant|hotel|mart|supermarket|bakery|pharmacy|clinic|hospital|traders?|trading|enterprises?|services|technologies|solutions|boutique|kitchen|sweets|fitness|studio|labs|stationery|store|shop)\b"""
        )
        for (line in lines) {
            if (businessKeywordRegex.containsMatchIn(line) && !isGenericQrLine(line)) {
                val candidate = cleanCompanyName(line)
                if (isValidCompanyName(candidate)) {
                    return candidate
                }
            }
        }

        // 4. First non-generic, non-instruction line on the QR code: a guess, so callers with a better signal skip it.
        if (!allowGuess) return null
        for (line in lines) {
            if (!isGenericQrLine(line)) {
                val candidate = cleanCompanyName(line)
                if (isValidCompanyName(candidate) && candidate.length in 3..50) {
                    return candidate
                }
            }
        }

        return null
    }

    /**
     * Generates preview text for a screenshot, strongest signal first:
     * a QR company name backed by a label, legal suffix or business word,
     * then the stored [title] with the amount ("Order confirmed • Total 42.00"),
     * then a guessed QR company name (the first non-generic line), then the OCR digest.
     */
    fun previewText(
        ocrText: String,
        fallbackDescription: String? = null,
        isQr: Boolean = false,
        title: String? = null,
    ): String {
        if (isQr) {
            extractCompanyName(ocrText, allowGuess = false)?.let { return it }
        }
        if (!title.isNullOrBlank()) {
            // Just the amount phrase ("Total 42.00"), not its whole row, which can carry
            // unrelated text merged from the same line of the screen.
            val amount = (priorityAmountRegex.findAll(ocrText).lastOrNull() ?: amountRegex.find(ocrText))
                ?.value?.trim()
            return clip(if (amount != null && amount !in title) "$title • $amount" else title)
        }
        if (isQr) {
            extractCompanyName(ocrText)?.let { return it }
        }
        return summarizeDigest(ocrText, fallbackDescription)
    }

    /**
     * Generates preview text for [image].
     * For QR codes, prioritizes the company name extracted from OCR text.
     */
    fun previewText(image: ImageRecord): String =
        previewText(
            ocrText = image.ocrText.ifBlank { image.rawOcrText },
            fallbackDescription = image.description,
            isQr = image.category == ImageCategory.QR ||
                image.subCategory?.contains("qr", ignoreCase = true) == true,
            title = image.title,
        )

    private val genericQrTokens = listOf(
        "scan to pay", "scan & pay", "scan and pay", "scan qr", "scan this qr",
        "scan here", "scan code", "scan me", "scanner", "pay with qr",
        "qr code", "qr", "qrcode", "barcode", "scan",
        "fonepay", "nepalpay", "smartqr", "esewa", "khalti", "upi",
        "bhim", "paytm", "gpay", "google pay", "phonepe",
        "accepted here", "powered by", "merchant id", "terminal id",
        "pan no", "vat no", "account no", "mobile banking",
        "internet banking", "any bank app", "download app",
        "open camera", "point camera", "view in browser",
    )

    private fun isGenericQrLine(line: String): Boolean {
        val lower = line.lowercase().trim()
        if (lower.length <= 2) return true
        if (genericQrTokens.any { lower == it || lower.startsWith("$it:") || lower.startsWith("$it -") || lower.startsWith("$it ") }) return true
        val words = lower.split(Regex("[\\s,.:/\\-]+")).filter { it.isNotBlank() }
        if (words.all { w -> genericQrTokens.any { it.contains(w) } || w in setOf("to", "with", "and", "or", "the", "a", "an", "at", "by", "in", "on", "for", "here", "app", "pay", "no", "id") }) {
            return true
        }
        return false
    }

    private val fieldLineRegex = Regex("""^[^:]{1,25}:\s""")

    private fun isValidCompanyName(name: String): Boolean {
        if (name.length < 3 || name.length > 60) return false
        if (isNoiseLine(name) || isGenericQrLine(name)) return false
        if (!name.any { it.isLetter() }) return false
        if (name.startsWith("http://") || name.startsWith("https://") || name.startsWith("www.")) return false
        // "VALID TILL: OCT 18" is a field, not a name; merchant labels were already read in step 1.
        if (fieldLineRegex.containsMatchIn(name)) return false
        if (amountRegex.containsMatchIn(name) && name.split(' ').size <= 2) return false
        return true
    }

    private fun cleanCompanyName(raw: String): String {
        val trimmed = raw
            .removePrefix("#")
            .removePrefix("##")
            .trim(' ', ':', '-', '#', '*', '"', '\'', '•', '·')
        return trimmed.removePrefix(".").removeSuffix("...").trim()
    }

    /** Extracts structured key-value highlights for the detail screen. */
    fun extractHighlights(ocrText: String): List<Pair<String, String>> {
        val highlights = mutableListOf<Pair<String, String>>()

        val amountMatch = priorityAmountRegex.findAll(ocrText).lastOrNull()
            ?: amountRegex.findAll(ocrText).lastOrNull()
        amountMatch?.let { match ->
            highlights += "Amount" to match.value.trim()
        }

        dateRegex.find(ocrText)?.let { match ->
            highlights += "Date" to match.groupValues.getOrElse(1) { match.value }.trim()
        }

        idRegex.find(ocrText)?.let { match ->
            highlights += "Reference" to match.value.trim()
        }

        return highlights
    }

    /** Cleans up raw OCR text into readable paragraphs for copy and full inspection. */
    fun formatCleanOcrText(ocrText: String): String {
        return ocrText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    /** 1 for a "# " title line, 2 for a "## " emphasized line, 3 for body text. */
    private fun headingLevel(line: String): Int = when {
        line.startsWith("# ") -> 1
        line.startsWith("## ") -> 2
        else -> 3
    }

    private fun headingText(line: String): String = line.removePrefix("## ").removePrefix("# ")

    private fun isNoiseLine(line: String): Boolean {
        val clean = line.trim().lowercase()
        if (clean.length <= 2) return true
        if (statusNoiseRegex.matches(clean)) return true
        val tokens = clean.split(tokenSplitRegex).filter { it.isNotBlank() }
        return tokens.isNotEmpty() && tokens.all { token ->
            statusNoiseRegex.matches(token)
        }
    }
}
