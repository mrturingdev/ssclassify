package com.mrturingdev.ssclassify.aicore.fallback

import com.mrturingdev.ssclassify.aicore.model.EntityType
import com.mrturingdev.ssclassify.aicore.model.InferenceSource
import com.mrturingdev.ssclassify.aicore.model.MeaningCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuleBasedOcrMeaningExtractorTest {

    @Test
    fun testExtract_receiptScenario() {
        val ocr = """
            09:41
            100%
            Blue Bottle Coffee
            1x Espresso $4.50
            1x Almond Croissant $5.00
            Subtotal $9.50
            Tax $0.85
            Total: $10.35
            Paid via Apple Pay
            Thank you!
        """.trimIndent()

        val result = RuleBasedOcrMeaningExtractor.extract(ocr)

        assertEquals(MeaningCategory.RECEIPT_EXPENSE, result.category)
        assertEquals(InferenceSource.RULE_BASED_FALLBACK, result.source)
        assertNotNull(result.firstEntity(EntityType.AMOUNT))
        assertTrue(result.headline.contains("Blue Bottle Coffee"))
        assertTrue(result.message.contains("Total: $10.35") || result.message.contains("Blue Bottle"))
    }

    @Test
    fun testExtract_flightTicketScenario() {
        val ocr = """
            Delta Air Lines
            Passenger: Jane Smith
            Flight: DL-402
            Boarding Gate: B14
            Departure: 14:30
            PNR: KLM982
        """.trimIndent()

        val result = RuleBasedOcrMeaningExtractor.extract(ocr)

        assertEquals(MeaningCategory.TRAVEL_TICKET, result.category)
        assertNotNull(result.firstEntity(EntityType.IDENTIFIER))
    }

    @Test
    fun testExtract_sensitiveInformationFlagged() {
        val ocr = "Your Google verification code is OTP: 839201. Never share this with anyone."
        val result = RuleBasedOcrMeaningExtractor.extract(ocr)

        assertTrue(result.isSensitive)
    }

    @Test
    fun testExtract_emptyTextHandledGracefully() {
        val result = RuleBasedOcrMeaningExtractor.extract("   \n  \t  ")

        assertEquals(MeaningCategory.UNCATEGORIZED, result.category)
        assertEquals(0.0f, result.confidence)
        assertEquals("No Text Detected", result.headline)
    }

    @Test
    fun testExtract_headlineComesFromLargestFontLine() {
        val result = RuleBasedOcrMeaningExtractor.extract("Settings\nAccount\n# Storage almost full\nFree up space soon")

        assertEquals("Storage almost full", result.headline)
        assertFalse(result.cleanText.contains("#"))
    }

    @Test
    fun testExtract_noReferenceFromInsideWordsAndNoDoublePeriod() {
        val result = RuleBasedOcrMeaningExtractor.extract(
            "# MEET UTOPAI X.\nElo scores from blind preference votes in our Video Arena.\nGemini Omni Flash 1.1 1123",
        )

        assertTrue(result.entities.none { it.type == EntityType.IDENTIFIER }, "got ${result.entities}")
        assertFalse(result.message.contains(".."), result.message)
    }

    @Test
    fun testExtract_amountKeepsDigitGrouping() {
        val result = RuleBasedOcrMeaningExtractor.extract("Payment successful\nAmount Rs. 1,250.00\nFee NPR 1,25,000")
        assertEquals(listOf("1,250.00", "1,25,000"), result.entities.filter { it.type == EntityType.AMOUNT }.map { it.value })
    }
}
