package com.mrturingdev.ssclassify.aicore.extractor

import com.mrturingdev.ssclassify.aicore.client.AiCoreClient
import com.mrturingdev.ssclassify.aicore.model.EntityType
import com.mrturingdev.ssclassify.aicore.model.InferenceSource
import com.mrturingdev.ssclassify.aicore.model.MeaningCategory
import com.mrturingdev.ssclassify.aicore.status.AiCoreStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultOcrMeaningExtractorTest {

    private class FakeAiCoreClient(
        initialStatus: AiCoreStatus = AiCoreStatus.Ready,
        private val responseToReturn: String? = null,
        private val shouldThrow: Boolean = false,
    ) : AiCoreClient {
        private val _status = MutableStateFlow(initialStatus)
        override val status: StateFlow<AiCoreStatus> = _status.asStateFlow()

        override suspend fun checkAvailability(): AiCoreStatus = _status.value
        override suspend fun prepare(): Boolean = _status.value is AiCoreStatus.Ready

        override suspend fun generateContent(prompt: String): String {
            if (shouldThrow) throw RuntimeException("Inference timeout")
            return responseToReturn ?: "{}"
        }

        override fun generateContentStream(prompt: String): Flow<String> {
            if (shouldThrow) throw RuntimeException("Streaming error")
            return flowOf(responseToReturn ?: "")
        }

        override fun close() {}
    }

    @Test
    fun testExtractMeaning_whenAiCoreReady_usesAiCoreResult() = runTest {
        val json = """
            {
              "headline": "Supermarket Receipt",
              "message": "Purchased items for $42.50 at Supermarket.",
              "category": "RECEIPT_EXPENSE",
              "confidence": 0.99,
              "entities": [
                {"type": "AMOUNT", "label": "Total", "value": "$42.50"}
              ]
            }
        """.trimIndent()

        val fakeClient = FakeAiCoreClient(
            initialStatus = AiCoreStatus.Ready,
            responseToReturn = json,
        )

        val extractor = DefaultOcrMeaningExtractor(client = fakeClient, preferAiCore = true)
        val result = extractor.extractMeaning("Supermarket\nTotal: $42.50")

        assertEquals(InferenceSource.AICORE_GEMINI_NANO, result.source)
        assertEquals("Supermarket Receipt", result.headline)
        assertEquals(MeaningCategory.RECEIPT_EXPENSE, result.category)
        assertEquals("$42.50", result.firstEntity(EntityType.AMOUNT))
    }

    @Test
    fun testExtractMeaning_whenAiCoreThrows_fallsBackGracefully() = runTest {
        val fakeClient = FakeAiCoreClient(
            initialStatus = AiCoreStatus.Ready,
            shouldThrow = true,
        )

        val extractor = DefaultOcrMeaningExtractor(client = fakeClient, preferAiCore = true)
        val result = extractor.extractMeaning("Blue Bottle Coffee\nTotal: $12.00")

        assertEquals(InferenceSource.RULE_BASED_FALLBACK, result.source)
        assertEquals(MeaningCategory.RECEIPT_EXPENSE, result.category)
        assertTrue(result.headline.contains("Blue Bottle Coffee"))
    }

    @Test
    fun testExtractMeaning_whenPreferAiCoreFalse_usesRuleBased() = runTest {
        val fakeClient = FakeAiCoreClient(initialStatus = AiCoreStatus.Ready)
        val extractor = DefaultOcrMeaningExtractor(client = fakeClient, preferAiCore = false)

        val result = extractor.extractMeaning("Flight DL120 Gate A4 Departure 12:30")
        assertEquals(InferenceSource.RULE_BASED_FALLBACK, result.source)
        assertEquals(MeaningCategory.TRAVEL_TICKET, result.category)
    }

    /** Like the real client on a phone without AICore: prepare() fails with Error, checkAvailability() says why. */
    private class NoAiCoreClient : AiCoreClient {
        private val _status = MutableStateFlow<AiCoreStatus>(AiCoreStatus.Unchecked)
        override val status: StateFlow<AiCoreStatus> = _status.asStateFlow()
        override suspend fun checkAvailability(): AiCoreStatus =
            AiCoreStatus.Unsupported("AICore package not installed").also { _status.value = it }
        override suspend fun prepare(): Boolean {
            _status.value = AiCoreStatus.Error("GenerativeModel init failed")
            return false
        }
        override suspend fun generateContent(prompt: String): String = error("not available")
        override fun generateContentStream(prompt: String): Flow<String> = error("not available")
        override fun close() {}
    }

    @Test
    fun testExtractMeaning_onDeviceWithoutAiCore_reportsUnsupportedNotError() = runTest {
        val client = NoAiCoreClient()
        val result = DefaultOcrMeaningExtractor(client = client, preferAiCore = true).extractMeaning("Order #ORD-1\nTotal 42.00")

        assertEquals(InferenceSource.RULE_BASED_FALLBACK, result.source)
        assertTrue(client.status.value is AiCoreStatus.Unsupported, "was ${client.status.value}")
    }
}
