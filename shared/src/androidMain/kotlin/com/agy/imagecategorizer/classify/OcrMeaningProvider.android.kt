package com.agy.imagecategorizer.classify

import com.agy.imagecategorizer.aicore.OcrMeaningExtractorFactory
import com.agy.imagecategorizer.aicore.extractor.OcrMeaningExtractor
import com.agy.imagecategorizer.aicore.model.EntityType
import com.agy.imagecategorizer.data.AndroidApp

/**
 * Android implementation of [OcrMeaningProvider], binding directly to the `:aicore` module.
 *
 * Utilizes Google Play Services Android AICore and Gemini Nano for edge semantic analysis,
 * and seamlessly falls back to high-speed deterministic rules if AICore is unavailable or busy.
 */
actual class OcrMeaningProvider actual constructor() {

    private val extractor: OcrMeaningExtractor by lazy {
        val appContext = try {
            AndroidApp.context
        } catch (_: Throwable) {
            null
        }

        if (appContext != null) {
            try {
                OcrMeaningExtractorFactory.create(appContext)
            } catch (_: Throwable) {
                OcrMeaningExtractorFactory.createRuleBasedOnly()
            }
        } else {
            OcrMeaningExtractorFactory.createRuleBasedOnly()
        }
    }

    actual suspend fun extractMeaning(rawOcrText: String, filteredText: String): ExtractedOcrMeaning {
        val input = rawOcrText.ifBlank { filteredText }
        if (input.isBlank()) {
            return ExtractedOcrMeaning(
                headline = "No text detected",
                message = "No readable text found in image.",
            )
        }

        val result = try {
            extractor.extractMeaning(input)
        } catch (_: Throwable) {
            // Fallback safety net
            com.agy.imagecategorizer.aicore.fallback.RuleBasedOcrMeaningExtractor.extract(input)
        }

        val subCategory = result.secondaryCategory
            ?: result.firstEntity(EntityType.ORGANIZATION_SENDER)

        val highlights = result.entities.map { it.label to it.value }

        return ExtractedOcrMeaning(
            headline = result.headline,
            message = result.message,
            categoryName = result.category.name,
            subCategory = subCategory,
            highlights = highlights,
            isSensitive = result.isSensitive,
        )
    }
}
