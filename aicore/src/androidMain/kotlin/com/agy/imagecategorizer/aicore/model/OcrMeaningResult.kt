package com.agy.imagecategorizer.aicore.model

/**
 * Structured semantic representation extracted from raw OCR text.
 *
 * @param headline A concise, informative title capturing what the screenshot is about (e.g. "Starbucks Receipt", "Flight AA-102 Boarding Pass").
 * @param message The synthesized natural-language core message, takeaway, or explanation of the text.
 * @param category The primary high-level semantic category.
 * @param secondaryCategory Optional sub-category or domain label (e.g. "Coffee Shop", "Domestic Airline", "Python Error").
 * @param entities Structured entities discovered in the text (Amounts, Dates, IDs, Merchants, Locations, Actions).
 * @param actionItems Explicit actionable items found in the message (e.g. "Check-in closes at 14:00", "Pay bill by Friday").
 * @param cleanText Reconstructed reading text, with noise, status bars, and OCR artifacts filtered out.
 * @param confidence Confidence score of the extraction between 0.0 and 1.0.
 * @param isSensitive Flag indicating whether sensitive personal info (OTP, credentials, payment card numbers) was detected.
 * @param source Which engine produced this result (AICore Gemini Nano vs Rule-based fallback).
 */
data class OcrMeaningResult(
    val headline: String,
    val message: String,
    val category: MeaningCategory,
    val secondaryCategory: String? = null,
    val entities: List<ExtractedEntity> = emptyList(),
    val actionItems: List<String> = emptyList(),
    val cleanText: String = "",
    val confidence: Float = 1.0f,
    val isSensitive: Boolean = false,
    val source: InferenceSource = InferenceSource.AICORE_GEMINI_NANO,
) {
    /** Helper to find the first entity value matching the given entity type. */
    fun firstEntity(type: EntityType): String? =
        entities.firstOrNull { it.type == type }?.value

    /** Returns all entity values matching the given entity type. */
    fun allEntities(type: EntityType): List<ExtractedEntity> =
        entities.filter { it.type == type }

    /** Convenient representation for logging and debugging. */
    fun toSummaryDigest(): String = buildString {
        append("[$category] $headline")
        if (entities.isNotEmpty()) {
            append(" (")
            append(entities.take(3).joinToString("; ") { "${it.label}: ${it.value}" })
            append(")")
        }
    }
}
