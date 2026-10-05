package com.mrturingdev.ssclassify.aicore.client

import com.mrturingdev.ssclassify.aicore.status.AiCoreStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Low-level client interface for interacting with on-device Android AICore and Gemini Nano.
 */
interface AiCoreClient : AutoCloseable {

    /** Current observable lifecycle status of AICore on the device. */
    val status: StateFlow<AiCoreStatus>

    /** Checks device compatibility and service readiness. */
    suspend fun checkAvailability(): AiCoreStatus

    /**
     * Initializes and warms up the on-device inference engine.
     * @return true if AICore is ready for generation, false otherwise.
     */
    suspend fun prepare(): Boolean

    /**
     * Generates text content asynchronously using the on-device Gemini Nano model.
     */
    suspend fun generateContent(prompt: String): String

    /**
     * Streams generated text tokens asynchronously as they are produced by the on-device model.
     */
    fun generateContentStream(prompt: String): Flow<String>
}
