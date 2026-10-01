package com.agy.imagecategorizer.classify

object ScreenshotContentSummarizer {

    private val statusNoiseRegex = OcrTextProcessor.statusNoise

    private val tokenSplitRegex = Regex("[\\s|•·\\-]+")

    private val amountRegex = Regex(
        """(?i)(?:total|grand\s*total|subtotal|amount|due|paid|price|balance|rs\.?|npr|\$)\s*[:=-]?\s*([$€£₹]?\s*\d+(?:[.,]\d{1,2})?)"""
    )

    private val priorityAmountRegex = Regex(
        """(?i)(?:total|grand\s*total|amount|due|paid)\s*[:=-]?\s*([$€£₹]?\s*\d+(?:[.,]\d{1,2})?)"""
    )

    private val dateRegex = Regex(
        """(?i)(?:date|dated)?\s*[:=-]?\s*(\b\d{1,4}[/-]\d{1,2}[/-]\d{1,4}\b|\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2},?\s+\d{2,4}\b)"""
    )

    private val idRegex = Regex(
        """(?i)(?:order|invoice|txn|transaction|ticket|pnr|id|ref|bill)\s*(?:no\.?|id|#)?\s*[:=-]?\s*([a-z0-9\-_]{4,20})"""
    )

    /** Generates a concise 1-2 line summary for preview cards and dialog headers. */
    fun summarizeDigest(ocrText: String, fallbackDescription: String? = null): String {
        val lines = ocrText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !isNoiseLine(it) }

        if (lines.isEmpty()) {
            return fallbackDescription?.takeIf { it.isNotBlank() } ?: "No text detected"
        }

        // Check if there is an amount or headline line
        val amountLine = lines.lastOrNull { priorityAmountRegex.containsMatchIn(it) }
            ?: lines.firstOrNull { amountRegex.containsMatchIn(it) }
        val titleLine = lines.firstOrNull { it.length in 4..60 && !amountRegex.containsMatchIn(it) } ?: lines.first()

        val digest = if (amountLine != null && amountLine != titleLine) {
            "$titleLine • $amountLine"
        } else {
            lines.take(2).joinToString(" • ")
        }

        return if (digest.length > 90) digest.take(87).trimEnd() + "..." else digest
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
