package com.mrturingdev.ssclassify.classify

import com.mrturingdev.ssclassify.aicore.OcrMeaningExtractorFactory
import com.mrturingdev.ssclassify.aicore.extractor.OcrMeaningExtractor
import com.mrturingdev.ssclassify.aicore.model.EntityType
import com.mrturingdev.ssclassify.aicore.model.InferenceSource
import com.mrturingdev.ssclassify.aicore.status.AiCoreStatus
import com.mrturingdev.ssclassify.data.AndroidApp
import com.mrturingdev.ssclassify.telemetry.AiCoreState

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

    actual val aiCoreState: AiCoreState
        get() = when (extractor.status.value) {
            AiCoreStatus.Ready -> AiCoreState.Ready
            is AiCoreStatus.Downloading -> AiCoreState.Downloading
            is AiCoreStatus.Unsupported -> AiCoreState.Unsupported
            is AiCoreStatus.Error -> AiCoreState.Error
            else -> AiCoreState.Unavailable
        }

    actual suspend fun extractMeaning(
        rawOcrText: String,
        filteredText: String,
        summaryText: String,
    ): ExtractedOcrMeaning {
        // Font-size-ranked focus text first; raw only when the focus region read nothing.
        val input = summaryText.ifBlank { rawOcrText }
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
            fromAiCore = result.source == InferenceSource.AICORE_GEMINI_NANO,
        )
    }
}
