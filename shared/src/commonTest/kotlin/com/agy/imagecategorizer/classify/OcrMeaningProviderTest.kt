package com.agy.imagecategorizer.classify

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OcrMeaningProviderTest {

    @Test
    fun testExtractMeaning_fromReceiptOcr() = runBlocking {
        val ocr = """
            09:41
            100%
            Blue Bottle Coffee #104
            1x Cold Brew $5.50
            Subtotal $5.50
            Total: $5.50
            Paid via Apple Pay
            Thank you!
        """.trimIndent()

        val provider = OcrMeaningProvider()
        val meaning = provider.extractMeaning(ocr)

        assertTrue(meaning.headline.isNotBlank())
        assertTrue(meaning.message.isNotBlank())
        assertTrue(meaning.message.contains("Blue Bottle") || meaning.message.contains("5.50"))
    }

    @Test
    fun testExtractMeaning_fromEmptyOcr() = runBlocking {
        val provider = OcrMeaningProvider()
        val meaning = provider.extractMeaning("", "")

        assertEquals("No text detected", meaning.headline)
        assertFalse(meaning.isSensitive)
    }
}
