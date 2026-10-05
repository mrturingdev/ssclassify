package com.mrturingdev.ssclassify.classify

/**
 * Platform-independent representation of semantic meaning extracted from OCR text.
 *
 * @param headline Crisp headline/title describing the content (e.g. "Trader Joe's Receipt").
 * @param message Synthesized natural language message and meaning of the screenshot.
 * @param categoryName High-level category name suggested by the semantic engine.
 * @param subCategory Specific object, merchant, or topic identified in the text.
 * @param highlights Structured key-value pairs (Amounts, Dates, References, etc.).
 * @param isSensitive Whether credentials, OTPs, or financial secrets were identified.
 */
data class ExtractedOcrMeaning(
    val headline: String,
    val message: String,
    val categoryName: String? = null,
    val subCategory: String? = null,
    val highlights: List<Pair<String, String>> = emptyList(),
    val isSensitive: Boolean = false,
)

/**
 * Multiplatform provider that extracts proper meaning and synthesized message
 * from raw OCR text.
 *
 * On Android, this delegates to the `:aicore` module (Gemini Nano on Android AICore with fallback).
 * On iOS, this runs fast heuristic extraction.
 */
expect class OcrMeaningProvider() {
    suspend fun extractMeaning(rawOcrText: String, filteredText: String = rawOcrText): ExtractedOcrMeaning
}
