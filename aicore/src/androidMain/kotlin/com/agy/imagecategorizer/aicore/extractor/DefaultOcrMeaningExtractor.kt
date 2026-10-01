package com.agy.imagecategorizer.aicore.extractor

import android.util.Log
import com.agy.imagecategorizer.aicore.client.AiCoreClient
import com.agy.imagecategorizer.aicore.fallback.RuleBasedOcrMeaningExtractor
import com.agy.imagecategorizer.aicore.model.InferenceSource
import com.agy.imagecategorizer.aicore.model.MeaningCategory
import com.agy.imagecategorizer.aicore.model.OcrMeaningResult
import com.agy.imagecategorizer.aicore.parser.OcrMeaningParser
import com.agy.imagecategorizer.aicore.prompt.OcrPromptBuilder
import com.agy.imagecategorizer.aicore.status.AiCoreStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/**
 * Standard implementation of [OcrMeaningExtractor].
 * Orchestrates on-device Gemini Nano inference via [AiCoreClient] and seamless
 * fallback to [RuleBasedOcrMeaningExtractor].
 *
 * @param client The AICore client instance.
 * @param preferAiCore If true, attempts AICore first. If false, always uses fast rule-based engine.
 */
class DefaultOcrMeaningExtractor(
    private val client: AiCoreClient,
    private val preferAiCore: Boolean = true,
) : OcrMeaningExtractor {

    companion object {
        private const val TAG = "OcrMeaningExtractor"
    }

    override val status: StateFlow<AiCoreStatus> = client.status

    override suspend fun checkAvailability(): AiCoreStatus {
        return if (preferAiCore) {
            client.checkAvailability()
        } else {
            AiCoreStatus.Unsupported("Preferring rule-based extractor by configuration.")
        }
    }

    override suspend fun extractMeaning(rawOcrText: String): OcrMeaningResult {
        if (rawOcrText.isBlank()) {
            return OcrMeaningResult(
                headline = "No Text Detected",
                message = "The image did not yield readable OCR text.",
                category = MeaningCategory.UNCATEGORIZED,
                cleanText = "",
                confidence = 0.0f,
                source = InferenceSource.RULE_BASED_FALLBACK,
            )
        }

        if (preferAiCore) {
            try {
                // Ensure model is ready or prepare it
                val currentStatus = client.status.value
                val isReady = currentStatus is AiCoreStatus.Ready || client.prepare()

                if (isReady) {
                    val prompt = OcrPromptBuilder.buildMeaningPrompt(rawOcrText)
                    val rawResponse = client.generateContent(prompt)
                    val parsed = OcrMeaningParser.parse(rawResponse, rawOcrFallback = rawOcrText)
                    return parsed.copy(source = InferenceSource.AICORE_GEMINI_NANO)
                } else {
                    Log.i(TAG, "AICore not ready ($currentStatus). Using rule-based fallback.")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "AICore inference error, falling back to rule-based engine", e)
            }
        }

        // Fallback execution
        return RuleBasedOcrMeaningExtractor.extract(rawOcrText)
    }

    override fun extractMeaningStream(rawOcrText: String): Flow<String> = flow {
        if (rawOcrText.isBlank()) {
            emit("No text detected")
            return@flow
        }

        if (preferAiCore && client.status.value is AiCoreStatus.Ready) {
            try {
                val prompt = OcrPromptBuilder.buildMeaningPrompt(rawOcrText)
                client.generateContentStream(prompt).collect { token ->
                    emit(token)
                }
                return@flow
            } catch (e: Throwable) {
                Log.w(TAG, "Streaming failed, falling back to one-shot fallback message", e)
            }
        }

        // If streaming not available, emit fallback message
        val fallback = RuleBasedOcrMeaningExtractor.extract(rawOcrText)
        emit(fallback.message)
    }
}
