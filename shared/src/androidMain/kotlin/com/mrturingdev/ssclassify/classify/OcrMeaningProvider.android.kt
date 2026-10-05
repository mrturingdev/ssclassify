package com.mrturingdev.ssclassify.classify

import com.mrturingdev.ssclassify.aicore.OcrMeaningExtractorFactory
import com.mrturingdev.ssclassify.aicore.extractor.OcrMeaningExtractor
import com.mrturingdev.ssclassify.aicore.model.EntityType
import com.mrturingdev.ssclassify.data.AndroidApp

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
            com.mrturingdev.ssclassify.aicore.fallback.RuleBasedOcrMeaningExtractor.extract(input)
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
