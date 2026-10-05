package com.mrturingdev.ssclassify.aicore.fallback

import com.mrturingdev.ssclassify.aicore.model.EntityType
import com.mrturingdev.ssclassify.aicore.model.InferenceSource
import com.mrturingdev.ssclassify.aicore.model.MeaningCategory
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
