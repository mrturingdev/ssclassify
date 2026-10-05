package com.mrturingdev.ssclassify.aicore

import android.content.Context
import com.mrturingdev.ssclassify.aicore.client.AiCoreClient
import com.mrturingdev.ssclassify.aicore.client.DefaultAiCoreClient
import com.mrturingdev.ssclassify.aicore.extractor.DefaultOcrMeaningExtractor
import com.mrturingdev.ssclassify.aicore.extractor.OcrMeaningExtractor
import com.mrturingdev.ssclassify.aicore.fallback.RuleBasedOcrMeaningExtractor
import com.mrturingdev.ssclassify.aicore.model.InferenceSource
import com.mrturingdev.ssclassify.aicore.model.OcrMeaningResult
import com.mrturingdev.ssclassify.aicore.status.AiCoreStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Configuration parameters for [OcrMeaningExtractor].
 */
data class ExtractorConfig(
    val preferAiCore: Boolean = true,
    val temperature: Float = 0.2f,
    val maxOutputTokens: Int = 1024,
)

/**
 * Factory for creating [OcrMeaningExtractor] instances.
 */
object OcrMeaningExtractorFactory {

    /**
     * Creates a production-ready [OcrMeaningExtractor] bound to Android AICore
     * with automatic rule-based fallback.
     */
    fun create(
        context: Context,
        config: ExtractorConfig = ExtractorConfig(),
    ): OcrMeaningExtractor {
        val client = DefaultAiCoreClient(
            context = context,
            temperature = config.temperature,
            maxOutputTokens = config.maxOutputTokens,
        )
        return DefaultOcrMeaningExtractor(
            client = client,
            preferAiCore = config.preferAiCore,
        )
    }

    /**
     * Creates an [OcrMeaningExtractor] with a custom or mocked [AiCoreClient] (useful for testing).
     */
    fun createWithClient(
        client: AiCoreClient,
        preferAiCore: Boolean = true,
    ): OcrMeaningExtractor {
        return DefaultOcrMeaningExtractor(
            client = client,
            preferAiCore = preferAiCore,
        )
    }

    /**
     * Creates a fast, offline rule-based extractor without initializing AICore services.
     */
    fun createRuleBasedOnly(): OcrMeaningExtractor {
        return object : OcrMeaningExtractor {
            private val _status = MutableStateFlow<AiCoreStatus>(
                AiCoreStatus.Unsupported("Rule-based standalone mode")
            )
            override val status: StateFlow<AiCoreStatus> = _status.asStateFlow()

            override suspend fun checkAvailability(): AiCoreStatus = _status.value

            override suspend fun extractMeaning(rawOcrText: String): OcrMeaningResult {
                return RuleBasedOcrMeaningExtractor.extract(rawOcrText)
            }

            override fun extractMeaningStream(rawOcrText: String): Flow<String> {
                val result = RuleBasedOcrMeaningExtractor.extract(rawOcrText)
                return flowOf(result.message)
            }
        }
    }
}
