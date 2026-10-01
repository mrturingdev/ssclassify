package com.agy.imagecategorizer.aicore.extractor

import com.agy.imagecategorizer.aicore.model.OcrMeaningResult
import com.agy.imagecategorizer.aicore.status.AiCoreStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level engine interface to extract semantic meaning, summary message,
 * categories, and key entities from raw OCR text.
 */
interface OcrMeaningExtractor {

    /** Current observable status of on-device AICore / Gemini Nano. */
    val status: StateFlow<AiCoreStatus>

    /** Checks availability of the underlying AI model. */
    suspend fun checkAvailability(): AiCoreStatus

    /**
     * Extracts full structured meaning, message, category, and entities from raw OCR text.
     *
     * If AICore / Gemini Nano is ready, on-device generative inference is used.
     * If AICore is unsupported, downloading, or encounters an inference error,
     * this automatically and gracefully falls back to deterministic rule-based extraction.
     */
    suspend fun extractMeaning(rawOcrText: String): OcrMeaningResult

    /**
     * Streams raw tokens from Gemini Nano during generation.
     * Falls back to emitting the complete rule-based synthesized message if AICore is not ready.
     */
    fun extractMeaningStream(rawOcrText: String): Flow<String>
}
