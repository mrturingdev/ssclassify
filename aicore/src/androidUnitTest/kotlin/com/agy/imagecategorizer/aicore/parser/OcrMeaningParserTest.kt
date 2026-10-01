package com.agy.imagecategorizer.aicore.parser

import com.agy.imagecategorizer.aicore.model.EntityType
import com.agy.imagecategorizer.aicore.model.MeaningCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OcrMeaningParserTest {

    @Test
    fun testParse_validJson() {
        val jsonOutput = """
            {
              "headline": "Trader Joe's Receipt",
              "message": "Purchased groceries totaling $28.40 at Trader Joe's on Oct 10.",
              "category": "RECEIPT_EXPENSE",
              "secondaryCategory": "Groceries",
              "confidence": 0.98,
              "entities": [
                {"type": "AMOUNT", "label": "Total", "value": "$28.40"},
                {"type": "ORGANIZATION_SENDER", "label": "Store", "value": "Trader Joe's"},
                {"type": "DATE_TIME", "label": "Date", "value": "Oct 10, 2026"}
              ],
              "actionItems": ["File tax deductible receipt"],
              "isSensitive": false,
              "cleanText": "Trader Joe's\nTotal: $28.40"
            }
        """.trimIndent()

        val result = OcrMeaningParser.parse(jsonOutput)

        assertEquals("Trader Joe's Receipt", result.headline)
        assertEquals("Purchased groceries totaling $28.40 at Trader Joe's on Oct 10.", result.message)
        assertEquals(MeaningCategory.RECEIPT_EXPENSE, result.category)
        assertEquals("Groceries", result.secondaryCategory)
        assertEquals(3, result.entities.size)
        assertEquals("$28.40", result.firstEntity(EntityType.AMOUNT))
        assertEquals("Trader Joe's", result.firstEntity(EntityType.ORGANIZATION_SENDER))
        assertEquals(listOf("File tax deductible receipt"), result.actionItems)
        assertFalse(result.isSensitive)
    }

    @Test
    fun testParse_markdownCodeFence() {
        val markdownOutput = """
            Here is the extraction from the image:
            ```json
            {
              "headline": "Flight AA128 Boarding Pass",
              "message": "Boarding pass for flight AA128 from JFK to LHR on Nov 1.",
              "category": "TRAVEL_TICKET",
              "confidence": 0.92,
              "entities": [
                {"type": "IDENTIFIER", "label": "Flight", "value": "AA128"},
                {"type": "LOCATION", "label": "Departure", "value": "JFK"}
              ]
            }
            ```
            Hope this helps!
        """.trimIndent()

        val result = OcrMeaningParser.parse(markdownOutput)

        assertEquals("Flight AA128 Boarding Pass", result.headline)
        assertEquals(MeaningCategory.TRAVEL_TICKET, result.category)
        assertEquals("AA128", result.firstEntity(EntityType.IDENTIFIER))
        assertEquals("JFK", result.firstEntity(EntityType.LOCATION))
    }

    @Test
    fun testParse_malformedJson_fallsBackGracefully() {
        val invalidOutput = "This is a raw text explanation without proper json brackets."
        val result = OcrMeaningParser.parse(invalidOutput, rawOcrFallback = "Fallback OCR Line")

        assertTrue(result.headline.isNotBlank())
        assertEquals(MeaningCategory.UNCATEGORIZED, result.category)
    }
}
